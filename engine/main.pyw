"""
נקודת הכניסה של המנוע: שרת ה-API + אייקון במגש המערכת.
pythonw main.pyw          → בלי חלון (כך זה רץ מ-Startup)
python main.pyw --no-tray → לבדיקות, עם לוג בטרמינל
"""
import argparse
import logging
import logging.handlers
import os
import socket
import subprocess
import sys
import threading
import time
from pathlib import Path

BASE = Path(__file__).resolve().parent
os.chdir(BASE)
sys.path.insert(0, str(BASE))

# ב-pythonw אין stdout/stderr; ספריות שכותבות אליהם היו קורסות
if sys.stdout is None:
    sys.stdout = open(os.devnull, "w", encoding="utf-8")
if sys.stderr is None:
    sys.stderr = open(os.devnull, "w", encoding="utf-8")

LOG_DIR = BASE / "logs"
LOG_DIR.mkdir(exist_ok=True)
handlers = [logging.handlers.RotatingFileHandler(LOG_DIR / "engine.log", maxBytes=2_000_000, backupCount=2,
                                                 encoding="utf-8")]
if sys.stdout.isatty():
    handlers.append(logging.StreamHandler())
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s", handlers=handlers)
log = logging.getLogger("engine")

import uvicorn  # noqa: E402

import config  # noqa: E402
import server  # noqa: E402
import updater  # noqa: E402


# ב-pythonw אין חלון: בלי זה קריסה נעלמת בלי עקבות. כל חריגה לא מטופלת נכתבת ללוג.
def _log_crash(exc_type, exc, tb):
    log.critical("unhandled exception", exc_info=(exc_type, exc, tb))


sys.excepthook = _log_crash
threading.excepthook = lambda a: _log_crash(a.exc_type, a.exc_value, a.exc_traceback)


def port_free(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        try:
            s.bind(("127.0.0.1", port))
            return True
        except OSError:
            return False


def wait_for_port(port: int, seconds: float) -> bool:
    """אחרי הפעלה מחדש, התהליך הקודם צריך רגע לשחרר את הפורט."""
    deadline = time.time() + seconds
    while time.time() < deadline:
        if port_free(port):
            return True
        time.sleep(0.5)
    return False


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=None)
    parser.add_argument("--no-tray", action="store_true")
    parser.add_argument("--wait-port", type=float, default=2, help="seconds to wait for the port")
    args = parser.parse_args()

    config.ensure_file()
    port = args.port or config.get()["port"]
    if not wait_for_port(port, args.wait_port):
        log.error("port %s is busy (another engine already running?), exiting", port)
        sys.exit(1)

    uv = uvicorn.Server(uvicorn.Config(server.app, host="127.0.0.1", port=port, log_config=None,
                                       access_log=False))
    restart = threading.Event()
    icon = None

    def stop(with_restart: bool):
        log.info("stopping (%s)", "restart" if with_restart else "exit from tray menu")
        if with_restart:
            restart.set()
        uv.should_exit = True
        if icon:
            icon.stop()

    updater.request_restart = lambda: stop(True)  # אחרי עדכון yt-dlp

    thread = threading.Thread(target=uv.run, name="uvicorn", daemon=True)
    thread.start()
    log.info("engine %s listening on 127.0.0.1:%s, saving to %s", server.VERSION, port, config.download_dir())
    updater.start_auto_updates()  # yt-dlp מתעדכן לבד: עכשיו ופעם ביום

    if args.no_tray:
        try:
            while thread.is_alive():
                thread.join(0.5)
        except KeyboardInterrupt:
            stop(False)
    else:
        icon = make_tray(stop)
        icon.run()  # חוסם עד "יציאה"

    uv.should_exit = True
    thread.join(10)

    if restart.is_set():
        log.info("restarting")
        argv = [a for a in sys.argv[1:] if not a.startswith("--wait-port")]
        flags = getattr(subprocess, "DETACHED_PROCESS", 0) | getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)
        subprocess.Popen([sys.executable, str(BASE / "main.pyw"), *argv, "--wait-port=20"],
                         creationflags=flags, close_fds=True)


def make_tray(stop):
    import pystray
    from PIL import Image

    def open_folder(_icon, _item):
        os.startfile(config.download_dir())

    menu = pystray.Menu(
        pystray.MenuItem("פתח תיקייה", open_folder, default=True),
        pystray.MenuItem("יציאה", lambda _i, _m: stop(False)),
    )
    return pystray.Icon("universal-downloader", Image.open(BASE / "icon.png"),
                        f"Universal Downloader {server.VERSION}", menu)


if __name__ == "__main__":
    main()
