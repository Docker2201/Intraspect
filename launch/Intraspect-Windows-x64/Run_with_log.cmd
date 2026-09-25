@echo off
cd /d "%~dp0"
set "PATH=%~dp0app\occt\win64;%PATH%"
"%~dp0Intraspect.exe"
echo Log: %~dp0intraspect-start.log
pause
