# TRENK – Universal Downloader

Save videos and audio (MP4 / MP3) from YouTube, Shorts, TikTok and Instagram – on Android and in Chrome.
Free, no ads, open source under **GPL-3.0**.

Download: https://levimtrenk-jpg.github.io/trenk/en/

## What's here

| Folder | What |
|---|---|
| `android/` | Android app (Kotlin + Jetpack Compose), built on [youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp + FFmpeg) |
| `engine/` | Local engine for Windows (Python, FastAPI + yt-dlp), listens on `127.0.0.1` only |
| `extension/` | Chrome extension (Manifest V3) that adds a download button and talks to the local engine |
| `site/` | The download page (GitHub Pages), Hebrew + English |
| `packaging/` | Windows installer |
| `legal/`, `THIRD_PARTY_NOTICES.md` | Third-party licenses |
| `ci/github/` | The CI that builds the official releases (signed APK, Windows ZIP). Kept here for reference; it runs in the private build repo |

## Building

- **Android:** JDK 17 + Gradle 8.11.1, then `cd android && gradle :app:assembleRelease`. Release signing reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` from the environment; without them you get an unsigned build.
- **Engine:** Python 3.11+, `pip install -r engine/requirements.txt`, then `python engine/main.pyw`.
- **Extension:** `chrome://extensions` → Developer mode → Load unpacked → `extension/`.

`config/*.json` contains example values. The official builds take the real values (contact address, notification topic, crash-report DSN) from CI secrets.

## License

TRENK is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License v3.0 – see [LICENSE](LICENSE). Bundled components keep their own licenses – see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

TRENK is not affiliated with YouTube, Google, TikTok, ByteDance, Instagram or Meta. Download only content you have the right to download.

---

# TRENK – מוריד סרטונים

שומרים וידאו ואודיו (MP4 / MP3) מיוטיוב, Shorts, טיקטוק ואינסטגרם – באנדרואיד ובכרום.
חינם, בלי פרסומות, קוד פתוח תחת **GPL-3.0**.

להורדה: https://levimtrenk-jpg.github.io/trenk/

## מה יש כאן

| תיקייה | מה |
|---|---|
| `android/` | אפליקציית אנדרואיד (Kotlin + Jetpack Compose), מבוססת על youtubedl-android (yt-dlp + FFmpeg) |
| `engine/` | מנוע מקומי לווינדוס (Python, FastAPI + yt-dlp), מאזין רק ל-`127.0.0.1` |
| `extension/` | תוסף כרום (Manifest V3) שמוסיף כפתור הורדה ומדבר עם המנוע המקומי |
| `site/` | דף ההורדה (GitHub Pages), עברית ואנגלית |
| `packaging/` | התקנה לווינדוס |
| `legal/`, `THIRD_PARTY_NOTICES.md` | רישיונות של רכיבי צד שלישי |
| `ci/github/` | ה-CI שבונה את הגרסאות הרשמיות (APK חתום, ZIP לווינדוס). לעיון; הוא רץ בריפו הבנייה הפרטי |

## בנייה

- **אנדרואיד:** JDK 17 ו-Gradle 8.11.1, ואז `cd android && gradle :app:assembleRelease`. החתימה נקראת ממשתני הסביבה `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; בלעדיהם נבנית גרסה לא חתומה.
- **מנוע:** Python 3.11 ומעלה, `pip install -r engine/requirements.txt`, ואז `python engine/main.pyw`.
- **תוסף:** `chrome://extensions` → מצב מפתח → טעינת תוסף לא ארוז → `extension/`.

ב-`config/*.json` יש ערכים לדוגמה. הבנייה הרשמית לוקחת את הערכים האמיתיים (כתובת יצירת קשר, topic להתראות, DSN לדיווחי קריסה) מ-Secrets של ה-CI.

## רישיון

TRENK הוא תוכנה חופשית: מותר להפיץ ולשנות אותה לפי תנאי GNU General Public License v3.0 – ראו [LICENSE](LICENSE). הרכיבים שבתוכה נשארים תחת הרישיונות שלהם – ראו [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

TRENK לא קשור ליוטיוב, גוגל, טיקטוק, ByteDance, אינסטגרם או Meta. הורידו רק תוכן שמותר לכם להוריד.
