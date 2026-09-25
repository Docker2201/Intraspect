@echo off
REM Intraspect - start the built portable app-image.
setlocal
cd /d "%~dp0"

set "APP="
if exist "launch\CNC_Modeling\Intraspect.exe" set "APP=%~dp0launch\CNC_Modeling"
if exist "launch\Intraspect\Intraspect.exe" set "APP=%~dp0launch\Intraspect"
if exist "launch\Intraspect_Extended_3.96\Intraspect.exe" set "APP=%~dp0launch\Intraspect_Extended_3.96"
if not defined APP (
    echo This is the source-code folder, not the ready-to-run application.
    echo Download the Windows ZIP, extract it and open Intraspect.exe:
    echo https://github.com/Docker2201/Intraspect/releases/latest
    echo.
    echo Developers: run build.bat to compile the source code.
    pause
    exit /b 1
)
set "PATH=%APP%\app\occt\win64;%PATH%"
"%APP%\Intraspect.exe" %*
if errorlevel 1 (
    echo.
    echo Intraspect exited with an error. For the messages, run:
    echo   launch\Run_Intraspect_with_console.cmd
    pause
    exit /b 1
)
