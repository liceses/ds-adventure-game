@echo off
rem  Package a stand-alone exe (jpackage app-image, bundles a runtime).
rem  ASCII only on purpose - see /(8.bat.
setlocal
pushd "%~dp0"
chcp 65001 >nul
if exist "tools\\launcher-hints-zh.txt" type "tools\\launcher-hints-zh.txt"

set "STAGE=build\\stage"
set "DIST=dist"
set "APP=%DIST%\\ds-adventure"

set "JP="
where jpackage >nul 2>nul && set "JP=jpackage"
if not defined JP if exist "%JAVA_HOME%\\bin\\jpackage.exe" set "JP=%JAVA_HOME%\\bin\\jpackage.exe"
if not defined JP if exist "C:\\Program Files\\Java\\jdk-21\\bin\\jpackage.exe" set "JP=C:\\Program Files\\Java\\jdk-21\\bin\\jpackage.exe"
if not defined JP goto nojpackage
echo [info] jpackage: %JP%

echo [1/5] building jar ...
call mvnw.cmd -q -DskipTests package
if errorlevel 1 goto fail

echo [2/5] collecting dependencies ...
call mvnw.cmd -q dependency:copy-dependencies -DoutputDirectory=target\\lib -DincludeScope=runtime
if errorlevel 1 goto fail

echo [3/5] staging ...
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%"
copy /y "target\\ds-adventure.jar" "%STAGE%\\" >nul
if errorlevel 1 goto fail
copy /y "target\\lib\\*.jar" "%STAGE%\\" >nul
if errorlevel 1 goto fail

echo [4/5] jpackage (1-2 minutes) ...
if exist "%APP%" rmdir /s /q "%APP%"
"%JP%" --type app-image --name ds-adventure --input "%STAGE%" --main-jar ds-adventure.jar --main-class com.studio.launcher.MainApp --java-options "-Dfile.encoding=UTF-8" --java-options "-Dapp.mode=player" --java-options "-Dstudio.map=$ROOTDIR\\maps\\story" --dest "%DIST%"
if errorlevel 1 goto fail

echo [5/5] copying maps + plugins + config next to the exe ...
xcopy /E /I /Y "maps" "%APP%\\maps" >nul
rem plugins.ini must ship next to the exe: without it event plugins (minigames) cannot be resolved by id
xcopy /E /I /Y "plugins" "%APP%\\plugins" >nul
copy /y "config.ini" "%APP%\\" >nul

echo.
echo ============================================================
echo  DONE: %APP%\\ds-adventure.exe
echo  (bundled: runtime + assets + maps + plugins)
echo  Double-click to play. No JDK needed on the target machine.
echo  You can zip and share the whole %APP% folder.
echo ============================================================
popd
endlocal
exit /b 0

:nojpackage
echo [FAILED] jpackage not found. Install JDK 17+ and set JAVA_HOME, or add its bin to PATH.
pause
popd
endlocal
exit /b 1

:fail
echo.
echo [FAILED] packaging did not finish. Check the message above.
pause
popd
endlocal
exit /b 1
