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
rem
rem  !! Never let the game JVM share this console.  Double-clicking a .bat always
rem  opens a cmd window; an attached JVM keeps it alive for the whole session, so
rem  the player sees "two windows, one after the other" (console + game).
rem  The game is started DETACHED through "start /b" + javaw:
rem    - javaw is the same JVM without a console of its own;
rem    - "start /b" keeps inheriting this console's redirection, so stdout/stderr
rem      still land in logs\launcher.log  (plain "start "" javaw" silently
rem      drops them - verified: the log file stays 0 bytes);
rem    - this window closes as soon as the script ends (~0.6 s) while the game
rem      window keeps running.
rem  Want the black window with live logs?  ->  launch this file with --console
rem ============================================================
setlocal
pushd "%~dp0"
chcp 65001 >nul
if exist "tools\launcher-hints-zh.txt" type "tools\launcher-hints-zh.txt"

set "MAP=maps/story"
set "JAR=target\ds-adventure.jar"
set "LIB=target\lib"
set "EXE=dist\ds-adventure\ds-adventure.exe"
set "LOGDIR=logs"
set "LOG=%LOGDIR%\launcher.log"

rem javaw = same JVM, no console window; prefer the JDK we already know about
set "JAVAW=javaw"
if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW=%JAVA_HOME%\bin\javaw.exe"

rem keep the black window only when explicitly requested
set "MODE=detached"
if /i "%~1"=="--console" set "MODE=console"
if /i "%~1"=="-console" set "MODE=console"

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
set "MAPARG="
if not exist "%MAP%\scenario.txt" goto nomap
set "MAPARG="%MAP%""
echo [launch] story build: Prologue + Ch.1-9 + Finale
goto launch

:nomap
echo [launch] map %MAP% not found - falling back to config.ini map.folder

:launch
if not exist "%LOGDIR%" mkdir "%LOGDIR%" >nul 2>nul
if /i "%MODE%"=="console" goto launchconsole
echo [launch] starting in the background; log file: %LOG%
echo ---- %DATE% %TIME% ---- >> "%LOG%"
start "" /b "%JAVAW%" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dapp.mode=player -cp "%JAR%;%LIB%\*" com.studio.launcher.MainApp player %MAPARG% >> "%LOG%" 2>&1
goto done

:launchconsole
echo [launch] console mode: this window stays open until the game exits
java -Dfile.encoding=UTF-8 -Dapp.mode=player -cp "%JAR%;%LIB%\*" com.studio.launcher.MainApp player %MAPARG%
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
