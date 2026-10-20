@echo off
chcp 65001 >nul
title Universal Downloader - התקנה
cd /d "%~dp0"

set "PYDIR=%LOCALAPPDATA%\Programs\Python\Python313"

echo [1/5] בודק Python...
if not exist "%PYDIR%\pythonw.exe" (
  echo     מתקין Python 3.13, זה לוקח כדקה...
  winget install -e --id Python.Python.3.13 --scope user --accept-package-agreements --accept-source-agreements
)
if not exist "%PYDIR%\pythonw.exe" (
  echo ❌ התקנת Python נכשלה. צלם את המסך ושלח למי ששלח לך את הקובץ.
  pause & exit /b 1
)
set "PY=%PYDIR%\python.exe"
set "PYW=%PYDIR%\pythonw.exe"

echo [2/5] בודק ffmpeg...
set "PATH=%LOCALAPPDATA%\Microsoft\WinGet\Links;%PATH%"
where ffmpeg >nul 2>&1 || winget install -e --id Gyan.FFmpeg --accept-package-agreements --accept-source-agreements

echo [3/5] מתקין את החבילות של המנוע...
"%PY%" -m pip install -q -U -r requirements.txt

echo [4/5] עוצר גרסה קודמת (אם רצה) ומסיר את ההפעלה האוטומטית הישנה...
set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
del "%STARTUP%\YT-Downloader.vbs" >nul 2>&1
rem יכולים לרוץ כמה תהליכים ישנים על אותו פורט: עוצרים עד שהפורט פנוי
powershell -NoProfile -Command "for ($i = 0; $i -lt 20; $i++) { $c = Get-NetTCPConnection -LocalPort 5123 -State Listen -ErrorAction SilentlyContinue; if (-not $c) { break }; $c | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }; Start-Sleep -Milliseconds 500 }"

echo [5/5] יוצר קיצור דרך ב-Startup (הפעלה עם Windows, בלי חלון)...
powershell -NoProfile -Command "$s = (New-Object -ComObject WScript.Shell).CreateShortcut('%STARTUP%\Universal Downloader.lnk'); $s.TargetPath = '%PYW%'; $s.Arguments = '\"%~dp0main.pyw\"'; $s.WorkingDirectory = '%~dp0'; $s.IconLocation = '%PYW%'; $s.Save()"
rem אותו קיצור דרך גם בתפריט התחל, כדי שיהיה קל להפעיל ידנית
powershell -NoProfile -Command "$s = (New-Object -ComObject WScript.Shell).CreateShortcut('%APPDATA%\Microsoft\Windows\Start Menu\Programs\Universal Downloader.lnk'); $s.TargetPath = '%PYW%'; $s.Arguments = '\"%~dp0main.pyw\"'; $s.WorkingDirectory = '%~dp0'; $s.IconLocation = '%PYW%'; $s.Save()"
start "" "%STARTUP%\Universal Downloader.lnk"

rem מחכה עד 20 שניות שהמנוע יענה (בלי timeout.exe, שלא עובד כשהקלט מנותב)
powershell -NoProfile -Command "for ($i = 0; $i -lt 40; $i++) { try { $h = Invoke-RestMethod http://127.0.0.1:5123/health -TimeoutSec 2; Write-Host ('engine ' + $h.version + ', yt-dlp ' + $h.ytdlp_version); exit 0 } catch { Start-Sleep -Milliseconds 500 } }; Write-Host 'ERROR: engine did not respond. See logs\engine.log'; exit 1"
if errorlevel 1 (pause & exit /b 1)
echo.
echo ✅ המנוע מוכן! הוא רץ ברקע (אייקון במגש ליד השעון) ויעלה לבד בכל הדלקה.
rem כשמופעל מ-Install.bat של החבילה, ממשיכים לשלב התוסף בלי לעצור
if /i not "%~1"=="/nopause" pause
exit /b 0
