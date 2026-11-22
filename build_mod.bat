@echo off
setlocal
cd /d "%~dp0"
echo.
echo === Quarry Client: build ===
set "JDK25="
for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*") do set "JDK25=%%d"
if not defined JDK25 (
  echo [ERROR] JDK 25 not found. Install Temurin 25 from adoptium.net, then run this file again.
  pause
  exit /b 1
)
set "JAVA_HOME=%JDK25%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using Java from: %JAVA_HOME%
if not exist work mkdir work
cd work
if not exist template (
  echo Downloading Fabric template...
  powershell -NoProfile -Command "try { Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/FabricMC/fabric-example-mod/archive/refs/heads/1.21.4.zip' -OutFile template.zip } catch { exit 1 }"
  if errorlevel 1 (
    echo [ERROR] Could not download the template. Check internet and send this message to Claude.
    pause
    exit /b 1
  )
  powershell -NoProfile -Command "Expand-Archive -Force template.zip ."
  ren fabric-example-mod-1.21.4 template
)
cd template
if exist src rmdir /s /q src
xcopy /e /i /y "..\..\src" src >nul
echo Building. The first time takes several minutes, please wait...
call gradlew.bat build --console=plain --no-daemon
if errorlevel 1 (
  echo.
  echo [ERROR] Build failed. Copy the text above and send it to Claude.
  pause
  exit /b 1
)
set "JAR="
for %%f in (build\libs\*.jar) do (
  echo %%f | findstr /i /c:"sources" >nul
  if errorlevel 1 set "JAR=%%f"
)
if not defined JAR (
  echo [ERROR] The jar file was not found in build\libs.
  pause
  exit /b 1
)
copy /y "%JAR%" "..\..\quarry-client.jar" >nul
set "MODS=%APPDATA%\.minecraft\mods"
if not exist "%MODS%" mkdir "%MODS%"
copy /y "..\..\quarry-client.jar" "%MODS%\quarry-client.jar" >nul
echo.
echo DONE. The mod was copied to: %MODS%\quarry-client.jar
pause
