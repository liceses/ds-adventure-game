@echo off
rem ============================================================
rem  Launch the story build (Prologue + Ch.1 + Ch.2).  ASCII only on purpose:
rem  cmd.exe mis-parses batch files containing non-ASCII bytes.
rem ============================================================
setlocal
pushd "%~dp0"
chcp 65001 >nul
if exist "tools\\launcher-hints-zh.txt" type "tools\\launcher-hints-zh.txt"

set "MAP=maps/story"
set "JAR=target\\ds-adventure.jar"
set "LIB=target\\lib"

if exist "dist\\ds-adventure\\ds-adventure.exe" (
    echo [launch] using packaged exe: dist\\ds-adventure\\ds-adventure.exe
    start "" "dist\\ds-adventure\\ds-adventure.exe"
    popd ^& endlocal ^& exit /b 0
)

if not exist "%JAR%" goto build
if not exist "%LIB%\\javafx-controls-17.0.20-win.jar" goto build
goto run

:build
echo [build] first run: compiling, about 1 minute...
call mvnw.cmd -q -DskipTests package
if errorlevel 1 goto fail
echo [build] collecting dependencies into %LIB% ...
call mvnw.cmd -q dependency:copy-dependencies -DoutputDirectory=%LIB% -DincludeScope=runtime
if errorlevel 1 goto fail

:run
if not exist "%MAP%\\scenario.txt" (
    echo [launch] map %MAP% not found - falling back to config.ini map.folder
    java -Dfile.encoding=UTF-8 -Dapp.mode=player -cp "%JAR%;%LIB%\\*" com.studio.launcher.MainApp player
) else (
    echo [launch] story build: Prologue / Chapter 1 / Chapter 2
    java -Dfile.encoding=UTF-8 -Dapp.mode=player -cp "%JAR%;%LIB%\\*" com.studio.launcher.MainApp player "%MAP%"
)
if errorlevel 1 goto fail
popd ^& endlocal
exit /b 0

:fail
echo.
echo [FAILED] see the message above. Send it to the game team.
pause
popd ^& endlocal
exit /b 1
