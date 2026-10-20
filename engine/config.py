"""
הגדרות המנוע: נקראות מ-config.json שליד הקוד.
הקובץ נטען מחדש אוטומטית כשהוא משתנה, אז אחרי עדכון ה-ID של התוסף אין צורך להפעיל מחדש.
"""
import json
import threading
from pathlib import Path

BASE = Path(__file__).parent
CONFIG_PATH = BASE / "config.json"

DEFAULTS = {
    # מזהי התוספים שמותר להם לפנות למנוע. ID קבוע (נגזר מ-"key" ב-extension/manifest.json), זהה אצל כל מי שמתקין
    "extension_ids": ["phdpbfhegbnkanfcibiicpaojjgljjpp"],
    "port": 5123,
    # ריק = %USERPROFILE%/Downloads/Videos
    "download_dir": "",
    # מאיזה דפדפן לקחת עוגיות לאינסטגרם. ריק = בלי.
    "instagram_cookies_browser": "chrome",
    # חלופה: נתיב לקובץ cookies.txt (פורמט Netscape). אם קיים, הוא קודם לדפדפן.
    "instagram_cookies_file": "",
}

_lock = threading.Lock()
_cache = {"mtime": None, "data": dict(DEFAULTS)}


def ensure_file():
    if not CONFIG_PATH.exists():
        CONFIG_PATH.write_text(json.dumps(DEFAULTS, indent=2, ensure_ascii=False), encoding="utf-8")


def get() -> dict:
    ensure_file()
    mtime = CONFIG_PATH.stat().st_mtime
    with _lock:
        if mtime != _cache["mtime"]:
            try:
                data = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
                _cache["data"] = {**DEFAULTS, **data}
                _cache["mtime"] = mtime
            except (OSError, ValueError):
                pass  # קובץ שבור באמצע עריכה: ממשיכים עם הגרסה הקודמת
        return dict(_cache["data"])


def download_dir() -> Path:
    custom = get()["download_dir"]
    path = Path(custom).expanduser() if custom else Path.home() / "Downloads" / "Videos"
    path.mkdir(parents=True, exist_ok=True)
    return path


def allowed_origins() -> set[str]:
    return {f"chrome-extension://{i.strip()}" for i in get()["extension_ids"] if i.strip()}
