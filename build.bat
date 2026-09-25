@echo off
REM Intraspect - build the portable Windows app-image.
REM
REM The real build lives in launch\build.ps1: it downloads the JDK, JavaFX and
REM RichTextFX it needs into launch\cache, compiles every source package and
REM packs a portable Intraspect with jpackage. Downloads stay in launch\cache.
setlocal
cd /d "%~dp0"

if not exist "launch\build.ps1" (
    echo ERROR: launch\build.ps1 not found next to this script.
    pause
    exit /b 1
)

echo Building Intraspect. The first run downloads about 300 MB and takes a few minutes.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "launch\build.ps1"
if errorlevel 1 (
    echo.
    echo ERROR: Build failed. Read the messages above.
    pause
    exit /b 1
)

echo.
echo Build completed. The output folder is shown above.
echo Start it with run.bat
echo.
pause
