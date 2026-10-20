# Universal Downloader – מנוע המחשב

מוריד מ-YouTube, Shorts, TikTok ו-Instagram (Reels, פוסטים, סטוריז) עם yt-dlp.
הקבצים נשמרים ב-`%USERPROFILE%\Downloads\Videos`.

## התקנה (חד-פעמית)
לחיצה כפולה על `install.bat`. הוא:
1. מתקין Python 3.13 ו-ffmpeg אם חסרים, ואת החבילות מ-`requirements.txt`.
2. עוצר את השרת הישן (YT Downloader) ומסיר את ההפעלה האוטומטית שלו.
3. יוצר קיצור דרך ב-Startup (וגם בתפריט התחל) שמריץ `pythonw main.pyw`: בלי חלון, עם אייקון במגש ליד השעון.

הסרה: `uninstall.bat`.

## אייקון במגש
- **פתח תיקייה** (גם לחיצה כפולה על האייקון)
- **עדכן** – מעדכן את yt-dlp ומפעיל מחדש
- **יציאה**

## הגדרות – `config.json`
| שדה | מה זה |
|---|---|
| `extension_ids` | רשימת ה-ID של תוסף הכרום (`chrome://extensions`). רק הוא יכול לפנות למנוע. נטען מחדש אוטומטית. |
| `port` | ברירת מחדל 5123 |
| `download_dir` | ריק = `Downloads\Videos` |
| `instagram_cookies_browser` | `chrome`. עובד רק כשכרום סגור (אחרת מסד העוגיות נעול). |
| `instagram_cookies_file` | נתיב ל-`cookies.txt` (פורמט Netscape). אמין יותר מהדפדפן; נדרש לסטוריז. |

## API (127.0.0.1:5123 בלבד)
| | |
|---|---|
| `GET /health` | `{ok, version, ytdlp_version, download_dir}` |
| `GET /formats?url=` | `{title, thumbnail, duration, uploader, platform, options:[{format_id, label, ext, height, filesize_approx}]}` |
| `POST /download` `{url, format_id}` | `{job_id}`. `format_id`: `mp4-<height>`, `mp4-best` או `mp3` |
| `GET /jobs/{id}` | `{status, percent, speed, eta, title, file_path, error}`. status: `queued` → `downloading` → `processing` → `done` / `error` |
| `POST /open` `{job_id?}` | פותח את תיקיית ההורדות; עם job_id מסמן את הקובץ ב-Explorer |
| `POST /update` | `pip install -U yt-dlp` ואז הפעלה מחדש. 409 אם יש הורדות פעילות. |

בקשה מדפדפן עם Origin שאינו `chrome-extension://<ID מה-config>` נדחית ב-403.

## פיתוח
```
python main.pyw --no-tray --port 5124
```
לוג: `logs/engine.log`.

השתמש רק בתוכן שמותר לך להוריד.
