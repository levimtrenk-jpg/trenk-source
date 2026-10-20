@echo off
chcp 65001 >nul
title Universal Downloader - התקנה
rem התקנה למחשב חדש מתוך ה-ZIP: מעתיק ל-%LOCALAPPDATA%\UniversalDownloader,
rem מתקין את המנוע (Python, ffmpeg, הפעלה עם Windows) ופותח את כרום לטעינת התוסף.

set "SRC=%~dp0"
set "DEST=%LOCALAPPDATA%\UniversalDownloader"

if not exist "%SRC%engine\main.pyw" (
  echo ❌ לא נמצאה תיקיית engine ליד הקובץ. חלץ את כל ה-ZIP לתיקייה ואז הפעל את Install.bat מתוכה.
  pause & exit /b 1
)

echo ==========================================
echo   Universal Downloader - התקנה
echo ==========================================
echo.
echo מעתיק קבצים ל-%DEST% ...
rem config.json לא נדרס בעדכון, כדי לשמור הגדרות קיימות
robocopy "%SRC%engine" "%DEST%\engine" /MIR /XD logs __pycache__ /XF config.json /NFL /NDL /NJH /NJS /NP >nul
if not exist "%DEST%\engine\config.json" copy "%SRC%engine\config.json" "%DEST%\engine\config.json" >nul
robocopy "%SRC%extension" "%DEST%\extension" /MIR /NFL /NDL /NJH /NJS /NP >nul
if not exist "%DEST%\engine\main.pyw" (
  echo ❌ ההעתקה נכשלה.
  pause & exit /b 1
)

echo.
call "%DEST%\engine\install.bat" /nopause
if errorlevel 1 exit /b 1

echo.
echo ==========================================
echo   שלב אחרון: התוסף לכרום (פעם אחת)
echo ==========================================
rem נתיב התוסף ללוח, כדי להדביק אותו בחלון בחירת התיקייה של כרום
powershell -NoProfile -Command "Set-Clipboard -Value '%DEST%\extension'"
start "" chrome "chrome://extensions" 2>nul || echo (פתח את כרום ידנית וכתוב בשורת הכתובת: chrome://extensions)
echo.
echo  1. בכרום שנפתח: הפעל את "מצב מפתח" / Developer mode (מתג למעלה מימין).
echo  2. לחץ "טעינת פריט לא ארוז" / Load unpacked.
echo  3. בחלון שנפתח: Ctrl+V בשורת הכתובת למעלה, Enter, ואז "בחירת תיקייה".
echo     (הנתיב כבר מועתק: %DEST%\extension)
echo.
echo זהו! ביוטיוב, טיקטוק ואינסטגרם יופיע כפתור TRENK להורדה.
echo הקבצים נשמרים ב: %USERPROFILE%\Downloads\Videos
echo.
pause
