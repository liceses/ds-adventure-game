@echo off
rem ============================================================
rem  Launch the story build (Prologue + Ch.1 + Ch.2).  ASCII only on purpose:
rem  cmd.exe mis-parses batch files containing non-ASCII bytes.
rem
rem  BUILD ORDER: the local build (target\ds-adventure.jar) wins when it exists;
rem  the packaged exe (dist\...) is only the fallback for machines with no build.
rem
rem  !! Never join "exit /b" with ^& inside an "if ( ... )" block.  cmd turns the
rem  caret-escaped ^& into literal text, so the exit never runs, the script falls
rem  through and launches the game a SECOND time  ->  two game windows.
rem  Use "goto <label>" to leave a branch instead.
rem ============================================================
setlocal
pushd "%~dp0"
chcp 65001 >nul
if exist "tools\launcher-hints-zh.txt" type "tools\launcher-hints-zh.txt"

set "MAP=maps/story"
set "JAR=target\ds-adventure.jar"
set "LIB=target\lib"
set "EXE=dist\ds-adventure\ds-adventure.exe"

rem --- pick the build: local jar first, packaged exe second, build last ---
if not exist "%JAR%" goto trypackaged
if not exist "%LIB%\javafx-controls-17.0.20-win.jar" goto trypackaged
goto run

:trypackaged
if exist "%EXE%" goto packaged
goto build

:build
echo [build] first run: compiling, about 1 minute...
call mvnw.cmd -q -DskipTests package
if errorlevel 1 goto fail
echo [build] collecting dependencies into %LIB% ...
call mvnw.cmd -q dependency:copy-dependencies -DoutputDirectory=%LIB% -DincludeScope=runtime
if errorlevel 1 goto fail

:run
if not exist "%MAP%\scenario.txt" (
    echo [launch] map %MAP% not found - falling back to config.ini map.folder
    java -Dfile.encoding=UTF-8 -Dapp.mode=player -cp "%JAR%;%LIB%\*" com.studio.launcher.MainApp player
) else (
    echo [launch] story build: Prologue / Chapter 1 / Chapter 2
    java -Dfile.encoding=UTF-8 -Dapp.mode=player -cp "%JAR%;%LIB%\*" com.studio.launcher.MainApp player "%MAP%"
)
if errorlevel 1 goto fail
goto done

:packaged
echo [launch] no local build - using packaged exe: %EXE%
start "" "%EXE%"
goto done

:fail
echo.
echo [FAILED] see the message above. Send it to the game team.
pause
popd
endlocal
exit /b 1

:done
popd
endlocal
exit /b 0
