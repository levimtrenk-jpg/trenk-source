@echo off
chcp 65001 >nul
set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
del "%STARTUP%\Universal Downloader.lnk" >nul 2>&1
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Universal Downloader.lnk" >nul 2>&1
powershell -NoProfile -Command "for ($i = 0; $i -lt 20; $i++) { $c = Get-NetTCPConnection -LocalPort 5123 -State Listen -ErrorAction SilentlyContinue; if (-not $c) { break }; $c | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }; Start-Sleep -Milliseconds 500 }"
echo ✅ ההפעלה האוטומטית הוסרה והמנוע נעצר.
pause
