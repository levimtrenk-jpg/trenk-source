"""
הלב של המנוע: yt-dlp כספרייה.
- probe(url): כותרת, תמונה, משך ורשימת אפשרויות הורדה.
- start(url, format_id): מתחיל הורדה ברקע ומחזיר job_id. ההתקדמות נשמרת ב-jobs.
"""
import logging
import re
import threading
import time
import unicodedata
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urlparse

import yt_dlp

import config
import updater
import updater

log = logging.getLogger("engine.downloader")

# דומיין → פלטפורמה. תת-דומיינים (m., vm., vt., music.) נתפסים אוטומטית.
PLATFORMS = {
    "youtube.com": "youtube",
    "youtu.be": "youtube",
    "youtube-nocookie.com": "youtube",
    "tiktok.com": "tiktok",
    "instagram.com": "instagram",
}

FORMAT_RE = re.compile(r"^(mp3|mp4-best|mp4-(\d{2,4}))$")

jobs: dict[str, dict] = {}
_jobs_lock = threading.Lock()
_pool = ThreadPoolExecutor(max_workers=3, thread_name_prefix="download")
MAX_JOBS_KEPT = 200


class DownloadFailed(Exception):
    """שגיאה שאפשר להציג למשתמש כמו שהיא. code="login_required": התוסף שולח עוגיות ומנסה שוב."""

    def __init__(self, message: str, code: str | None = None):
        super().__init__(message)
        self.code = code


# עוגיות אינסטגרם שהתוסף מייצא מכרום אוטומטית (POST /cookies)
AUTO_COOKIES = Path(__file__).parent / "instagram_cookies.txt"


# ---------- עזרים ----------

def platform_of(url: str) -> str | None:
    try:
        host = (urlparse(url.strip()).hostname or "").lower()
    except ValueError:
        return None
    for domain, name in PLATFORMS.items():
        if host == domain or host.endswith("." + domain):
            return name
    return None


class _YtdlpLogger:
    def debug(self, msg):
        if not msg.startswith("[debug] "):
            log.debug(msg)

    def info(self, msg):
        log.debug(msg)

    def warning(self, msg):
        log.warning(msg)

    def error(self, msg):
        log.error(msg)


def _base_opts(platform: str) -> dict:
    opts = {
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "noplaylist": True,
        "playlist_items": "1",  # פוסט אינסטגרם עם כמה סרטונים: לוקחים את הראשון
        "logger": _YtdlpLogger(),
        "socket_timeout": 20,
        # IPv6 במחשב הזה נתקע ~20 שניות לכל בקשה ליוטיוב. IPv4 מהיר.
        "source_address": "0.0.0.0",
        "retries": 5,
        "concurrent_fragment_downloads": 4,
        # יוטיוב דורש מנוע JavaScript. node כבר מותקן במחשב; deno אם יותקן בעתיד.
        "js_runtimes": {"deno": {}, "node": {}},
    }
    return opts


def _cookie_variants(platform: str) -> list[dict]:
    """לאינסטגרם: קודם עם עוגיות (קובץ או דפדפן), ואם טעינת העוגיות נכשלת, בלי."""
    if platform != "instagram":
        return [{}]
    cfg = config.get()
    variants = []
    cookie_file = cfg.get("instagram_cookies_file")
    if cookie_file and Path(cookie_file).expanduser().is_file():
        variants.append({"cookiefile": str(Path(cookie_file).expanduser())})
    if AUTO_COOKIES.is_file():
        variants.append({"cookiefile": str(AUTO_COOKIES)})
    if cfg.get("instagram_cookies_browser"):
        variants.append({"cookiesfrombrowser": (cfg["instagram_cookies_browser"],)})
    variants.append({})
    return variants


def _is_cookie_error(exc: Exception) -> bool:
    text = str(exc).lower()
    return any(k in text for k in ("cookie", "dpapi", "decrypt", "keyring"))


def _run_with_cookies(platform: str, opts: dict, fn):
    """מריץ fn(ydl). אם העוגיות לא נטענות (כרום פתוח, הצפנה וכו'), מנסה את האפשרות הבאה."""
    variants = _cookie_variants(platform)
    for i, extra in enumerate(variants):
        try:
            with yt_dlp.YoutubeDL({**opts, **extra}) as ydl:
                return fn(ydl)
        except Exception as e:  # noqa: BLE001
            if extra and _is_cookie_error(e) and i < len(variants) - 1:
                log.warning("cookies failed (%s), retrying without: %s", list(extra)[0], e)
                continue
            raise


def _pick_entry(info: dict) -> dict:
    if info.get("_type") in ("playlist", "multi_video") or "entries" in info:
        entries = [e for e in (info.get("entries") or []) if e]
        if not entries:
            raise DownloadFailed("לא נמצא סרטון בקישור הזה")
        return entries[0]
    return info


def _error_text(exc: Exception) -> str:
    return re.sub(r"\x1b\[[0-9;]*m", "", str(exc)).replace("ERROR: ", "").strip()


def _needs_login(exc: Exception, platform: str) -> bool:
    low = _error_text(exc).lower()
    return platform == "instagram" and any(k in low for k in ("login", "log in", "rate-limit", "not available", "cookies"))


def _repairable(exc: Exception, platform: str) -> bool:
    """שגיאה שעדכון yt-dlp עשוי לפתור (האתר השתנה). פרטי / לא נתמך / אין רשת / התחברות – לא."""
    if isinstance(exc, DownloadFailed) or _needs_login(exc, platform):
        return False
    low = _error_text(exc).lower()
    return not any(k in low for k in ("private", "unsupported url", "getaddrinfo", "failed to resolve",
                                      "network is unreachable", "no space left"))


def _friendly_error(exc: Exception, platform: str) -> DownloadFailed:
    low = _error_text(exc).lower()
    if _needs_login(exc, platform):
        return DownloadFailed("אינסטגרם דורש התחברות לתוכן הזה. היכנס לאינסטגרם בכרום ונסה שוב.", "login_required")
    if "private" in low:
        return DownloadFailed("התוכן פרטי ואי אפשר להוריד אותו.")
    if "unsupported url" in low:
        return DownloadFailed("הקישור לא נתמך")
    if any(k in low for k in ("getaddrinfo", "failed to resolve", "network is unreachable")):
        return DownloadFailed("אין חיבור לאינטרנט")
    if any(k in low for k in ("video unavailable", "has been removed", "no longer available")):
        return DownloadFailed("הסרטון הוסר או לא זמין.")
    # כאן כבר ניסינו לעדכן את yt-dlp ולנסות שוב
    return DownloadFailed("לא הצלחנו להוריד את הסרטון הזה, גם אחרי תיקון אוטומטי. נסה שוב מאוחר יותר או סרטון אחר.")


def _with_repair(platform: str, fn, on_repair=None):
    """תיקון עצמי: אם fn נכשל בשגיאה שעדכון עשוי לפתור – מעדכנים את yt-dlp ומנסים שוב פעם אחת."""
    try:
        return fn()
    except Exception as e:  # noqa: BLE001
        if not _repairable(e, platform):
            raise
        log.warning("failed, repairing (update yt-dlp + retry once): %s", _error_text(e)[:300])
        if on_repair:
            on_repair()
        if updater.repair():
            # גרסה חדשה נטענת רק בתהליך חדש: התוסף מחכה שהמנוע יחזור ומנסה שוב לבד
            updater.restart_soon()
            raise DownloadFailed("מתקן... רגע", "restarting") from e
        return fn()


_RESERVED = {"CON", "PRN", "AUX", "NUL", *(f"COM{i}" for i in range(1, 10)), *(f"LPT{i}" for i in range(1, 10))}


def clean_filename(info: dict, platform: str) -> str:
    """שם קובץ נקי מהכותרת: בלי האשטגים, אימוג'ים ותווים אסורים ב-Windows."""
    title = info.get("title") or info.get("description") or ""
    # כותרות גנריות ("Video by X", "TikTok video #123") → מוסיפים יוצר ומזהה כדי שלא יתנגשו
    if re.match(r"^(video by |tiktok video)", title, re.I):
        title = f"{info.get('uploader') or info.get('channel') or platform} {info.get('id') or ''}"
    no_tags = re.sub(r"#[^\s\d]\S*", " ", title)  # האשטגים, לא מספרים
    if no_tags.strip():
        title = no_tags
    chars = []
    for ch in title:
        cat = unicodedata.category(ch)
        if cat[0] == "C" or cat == "So" or 0xFE00 <= ord(ch) <= 0xFE0F:
            chars.append(" ")
        elif ch in '<>:"/\\|?*':
            chars.append(" ")
        else:
            chars.append(ch)
    name = re.sub(r"\s+", " ", "".join(chars)).strip(" .-_")
    if len(name) > 120:
        name = name[:120].rsplit(" ", 1)[0].strip(" .-_")
    if not name:
        name = f"{platform}_{info.get('id') or uuid.uuid4().hex[:8]}"
    if name.upper() in _RESERVED:
        name += "_"
    return name


def _unique_name(folder: Path, name: str, ext: str) -> str:
    candidate, n = name, 2
    while (folder / f"{candidate}.{ext}").exists():
        candidate = f"{name} ({n})"
        n += 1
    return candidate


def _size(fmt: dict) -> int | None:
    return fmt.get("filesize") or fmt.get("filesize_approx")


# ---------- probe ----------

def probe(url: str) -> dict:
    platform = platform_of(url)
    if not platform:
        raise DownloadFailed("הקישור לא נתמך. נתמכים: YouTube, Shorts, TikTok, Instagram")
    try:
        info = _with_repair(platform, lambda: _run_with_cookies(
            platform, _base_opts(platform), lambda ydl: ydl.extract_info(url, download=False)))
    except DownloadFailed:
        raise
    except Exception as e:  # noqa: BLE001
        log.error("probe failed %s: %s", url, _error_text(e)[:500])
        raise _friendly_error(e, platform) from e
    info = _pick_entry(info)
    formats = info.get("formats") or [info]

    def has_video(f):
        return f.get("vcodec") != "none" and f.get("height")

    audio_only = [f for f in formats if f.get("vcodec") == "none" and f.get("acodec") not in (None, "none")]
    best_audio = max((_size(f) or 0 for f in audio_only), default=0)

    by_height: dict[int, int | None] = {}
    short_side: dict[int, int] = {}
    for f in formats:
        if not has_video(f):
            continue
        h = int(f["height"])
        short_side[h] = min(h, int(f.get("width") or h))
        size = _size(f)
        if size and f.get("acodec") in (None, "none") and best_audio:
            size += best_audio  # ימוזג עם אודיו
        prev = by_height.get(h)
        # מעדיפים h264 (מתנגן בכל מקום), ואת הגודל שלו
        if h not in by_height or (str(f.get("vcodec", "")).startswith("avc") and size):
            by_height[h] = size or prev

    options = [
        {"format_id": f"mp4-{h}", "label": f"MP4 {short_side[h]}p", "ext": "mp4", "height": h, "filesize_approx": by_height[h]}
        for h in sorted(by_height, reverse=True)
    ]
    if not options:
        options.append({"format_id": "mp4-best", "label": "MP4", "ext": "mp4", "height": None,
                        "filesize_approx": _size(info)})
    options.append({"format_id": "mp3", "label": "MP3", "ext": "mp3", "height": None,
                    "filesize_approx": int(best_audio * 1.3) if best_audio else None})

    return {
        "url": info.get("webpage_url") or url,
        "platform": platform,
        "id": info.get("id"),
        "title": info.get("title"),
        "uploader": info.get("uploader") or info.get("channel"),
        "thumbnail": info.get("thumbnail"),
        "duration": info.get("duration"),
        "options": options,
    }


# ---------- download ----------

def _format_opts(format_id: str) -> tuple[dict, str]:
    m = FORMAT_RE.match(format_id or "")
    if not m:
        raise DownloadFailed("format_id לא תקין")
    if format_id == "mp3":
        return {
            "format": "bestaudio/best",
            "postprocessors": [{"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "192"}],
        }, "mp3"
    if format_id == "mp4-best":
        fmt = "bv*+ba/b"
    else:
        h = int(m.group(2))
        fmt = f"bv*[height={h}]+ba/b[height={h}]/bv*[height<={h}]+ba/b[height<={h}]/b"
    return {
        "format": fmt,
        "format_sort": ["vcodec:h264", "acodec:aac"],  # מתנגן בכל נגן
        "merge_output_format": "mp4",
        "postprocessors": [{"key": "FFmpegVideoRemuxer", "preferedformat": "mp4"}],
    }, "mp4"


def _new_job(url: str, format_id: str, platform: str) -> dict:
    job = {
        "id": uuid.uuid4().hex,
        "url": url,
        "format_id": format_id,
        "platform": platform,
        "status": "queued",  # queued → downloading → processing → done | error
        "percent": 0.0,
        "speed": None,  # בתים לשנייה
        "eta": None,  # שניות
        "title": None,
        "file_path": None,
        "error": None,
        "created": time.time(),
    }
    with _jobs_lock:
        jobs[job["id"]] = job
        if len(jobs) > MAX_JOBS_KEPT:
            for old in sorted(jobs.values(), key=lambda j: j["created"])[: len(jobs) - MAX_JOBS_KEPT]:
                if old["status"] in ("done", "error"):
                    jobs.pop(old["id"], None)
    return job


def start(url: str, format_id: str) -> str:
    platform = platform_of(url)
    if not platform:
        raise DownloadFailed("הקישור לא נתמך")
    _format_opts(format_id)  # בדיקת תקינות לפני שיוצרים משימה
    job = _new_job(url, format_id, platform)
    _pool.submit(_run, job)
    return job["id"]


def _run(job: dict):
    platform = job["platform"]
    fmt_opts, ext = _format_opts(job["format_id"])
    folder = config.download_dir()
    parts: list[dict] = []  # [{id, done, total}] לכל קובץ שיורד (וידאו + אודיו)

    def progress(d):
        fid = (d.get("info_dict") or {}).get("format_id")
        part = next((p for p in parts if p["id"] == fid), None)
        if part is None:
            part = {"id": fid, "done": 0, "total": None}
            parts.append(part)
        total = d.get("total_bytes") or d.get("total_bytes_estimate")
        if total:
            part["total"] = total
        if d["status"] == "downloading":
            job["status"] = "downloading"
            part["done"] = d.get("downloaded_bytes") or 0
            job["speed"] = d.get("speed")
            job["eta"] = d.get("eta")
        elif d["status"] == "finished":
            part["done"] = part["total"] or part["done"]
        if all(p["total"] for p in parts):
            pct = 100 * sum(p["done"] for p in parts) / sum(p["total"] for p in parts)
        else:
            idx = parts.index(part)
            frac = part["done"] / part["total"] if part["total"] else 0
            pct = 100 * (idx + frac) / len(parts)
        job["percent"] = round(min(max(pct, job["percent"]), 100), 1)  # לא יורד אחורה

    def postprocess(d):
        if d["status"] == "started":
            job["status"] = "processing"
            job["percent"] = 100.0
            job["speed"] = job["eta"] = None

    def finished(path):
        job["file_path"] = str(Path(path).resolve())

    def work(ydl):
        info = _pick_entry(ydl.extract_info(job["url"], download=False))
        job["title"] = info.get("title")
        name = _unique_name(folder, clean_filename(info, platform), ext)
        ydl.params["outtmpl"] = {"default": str(folder / (name.replace("%", "%%") + ".%(ext)s"))}
        # האומדן מראש לכל חלק, כדי שהאחוז יתקדם ברציפות גם כשיש וידאו + אודיו
        for f in info.get("requested_formats") or [info]:
            parts.append({"id": f.get("format_id"), "done": 0, "total": _size(f)})
        ydl.process_ie_result(info, download=True)

    opts = {
        **_base_opts(platform),
        **fmt_opts,
        "progress_hooks": [progress],
        "postprocessor_hooks": [postprocess],
        "post_hooks": [finished],
    }
    try:
        job["status"] = "downloading"

        def repairing():
            parts.clear()
            job.update(status="repairing", percent=0.0, speed=None, eta=None, file_path=None)

        _with_repair(platform, lambda: _run_with_cookies(platform, opts, work), repairing)
        if not job["file_path"]:
            raise DownloadFailed("ההורדה הסתיימה בלי קובץ")
        job.update(status="done", percent=100.0, speed=None, eta=None)
        log.info("done %s → %s", job["url"], job["file_path"])
    except Exception as e:  # noqa: BLE001
        failed = e if isinstance(e, DownloadFailed) else _friendly_error(e, platform)
        job.update(status="error", error=str(failed), code=failed.code, speed=None, eta=None)
        log.error("failed %s: %s", job["url"], e)
