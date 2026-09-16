@echo off
rem  Launch the Studio editor (ASCII only on purpose - see /(8.bat).
rem  NOTE: never join "exit /b" with ^& inside an "if ( ... )" block - cmd turns the
rem  caret-escaped ^& into literal text and the exit silently never runs.
setlocal
pushd "%~dp0"
chcp 65001 >nul
if exist "tools\launcher-hints-zh.txt" type "tools\launcher-hints-zh.txt"

set "JAR=target\ds-adventure.jar"
set "LIB=target\lib"

if not exist "%JAR%" goto build
if not exist "%LIB%\javafx-controls-17.0.20-win.jar" goto build
goto run

:build
echo [build] first run: compiling, about 1 minute...
call mvnw.cmd -q -DskipTests package
if errorlevel 1 goto fail
echo [build] collecting dependencies into %LIB% ...
call mvnw.cmd -q dependency:copy-dependencies -DoutputDirectory=%LIB% -DincludeScope=runtime
if errorlevel 1 goto fail

:run
echo [launch] Studio editor
java -Dfile.encoding=UTF-8 -Dapp.mode=editor -cp "%JAR%;%LIB%\*" com.studio.launcher.MainApp studio
if errorlevel 1 goto fail
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
