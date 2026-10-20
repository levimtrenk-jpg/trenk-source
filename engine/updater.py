"""
עדכון yt-dlp אוטומטי, בלי כפתורים: בהפעלת המנוע, פעם ביום, ותיקון עצמי כשחילוץ/הורדה נכשלים.
כשהגרסה משתנה המנוע מופעל מחדש (טעינה מחדש של yt-dlp בתוך התהליך שוברת את ה-patch שלו ל-urllib3).
"""
import logging
import subprocess
import sys
import threading
import time
from pathlib import Path

import yt_dlp.version

log = logging.getLogger("engine.updater")

DAY = 24 * 3600
RETRY_FAILED = 3600    # עדכון שנכשל (אין רשת וכו'): ניסיון נוסף בעוד שעה
REPAIR_COOLDOWN = 600  # תיקון עצמי לא מריץ pip שוב אם עודכן ב-10 הדקות האחרונות
STARTUP_DELAY = 20     # לא להאט את עליית המנוע
RESTART_DELAY = 5      # זמן לתשובה האחרונה (והתוסף רואה את סטטוס המשימה) לפני הפעלה מחדש

# main.pyw מחבר כאן פונקציה שמפעילה את המנוע מחדש
request_restart = None

_lock = threading.Lock()
_last_update = 0.0
_restart_pending = False


def current_version() -> str:
    return yt_dlp.version.__version__


def _python_exe() -> str:
    # pythonw לא מחזיר פלט; ל-pip משתמשים ב-python.exe שלידו
    exe = Path(sys.executable)
    return str(exe.with_name("python.exe") if exe.name.lower() == "pythonw.exe" else exe)


def _busy() -> bool:
    import downloader
    return any(j["status"] in ("queued", "downloading", "processing", "repairing")
               for j in list(downloader.jobs.values()))


def update(reason: str) -> dict:
    """pip install -U yt-dlp. מחזיר את הגרסה לפני ואחרי; changed=True אם צריך להפעיל מחדש."""
    global _last_update, _restart_pending
    with _lock:
        no_window = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        before = current_version()
        log.info("yt-dlp update (%s): checking, current %s", reason, before)
        try:
            proc = subprocess.run([_python_exe(), "-m", "pip", "install", "-q", "-U", "yt-dlp[default,curl-cffi]"],
                                  capture_output=True, text=True, timeout=300, creationflags=no_window)
            after = subprocess.run([_python_exe(), "-c", "import yt_dlp.version as v; print(v.__version__)"],
                                   capture_output=True, text=True, timeout=60, creationflags=no_window).stdout.strip()
        except (OSError, subprocess.SubprocessError) as e:
            proc, after = None, ""
            error = str(e)
        else:
            error = proc.stderr.strip()[-500:] if proc.returncode else None
        if error:
            _last_update = time.time() - DAY + RETRY_FAILED
            log.error("yt-dlp update (%s) failed: %s", reason, error)
            return {"ok": False, "before": before, "after": before, "changed": False, "error": error}
        _last_update = time.time()
        after = after or before
        changed = after != before
        if changed:
            _restart_pending = True
            log.info("yt-dlp updated %s -> %s, engine will restart", before, after)
        else:
            log.info("yt-dlp already up to date (%s)", before)
        return {"ok": True, "before": before, "after": after, "changed": changed, "error": None}


def repair() -> bool:
    """תיקון עצמי, לפני ניסיון חוזר אחד. True = הותקנה גרסה חדשה וצריך הפעלה מחדש לפני הניסיון."""
    if time.time() - _last_update < REPAIR_COOLDOWN:
        log.info("repair: updated recently, retrying without pip")
        return _restart_pending
    return update("repair")["changed"]


def _restart_when_idle():
    while _busy():
        time.sleep(2)
    time.sleep(RESTART_DELAY)
    if request_restart:
        log.info("restarting to load the new yt-dlp")
        request_restart()


def restart_soon():
    threading.Thread(target=_restart_when_idle, name="ytdlp-restart", daemon=True).start()


def _loop():
    time.sleep(STARTUP_DELAY)
    while True:
        if time.time() - _last_update >= DAY:
            while _busy():  # לא מעדכנים באמצע הורדה
                time.sleep(60)
            if update("startup" if _last_update == 0 else "daily")["changed"]:
                restart_soon()
        time.sleep(3600)


def start_auto_updates():
    threading.Thread(target=_loop, name="ytdlp-updater", daemon=True).start()
