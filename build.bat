@echo off
rem Build standalone merger.jar (CLI + GUI + embedded transformer/runtime)
rem Requires: Advanced-Menu.jar in this folder, JDK on PATH
setlocal
set ROOT=%~dp0
set STAGE=%TEMP%\merger_stage
rmdir /s /q "%STAGE%" 2>nul
mkdir "%STAGE%"
mkdir merger\out 2>nul

cd /d "%STAGE%"
jar xf "%ROOT%Advanced-Menu.jar" classes icon.png MZMODlib.jar MZMODlibs.jar MZMODSSlib.jar
if errorlevel 1 exit /b 1

cd /d "%ROOT%"
javac -cp Advanced-Menu.jar -d merger\out merger\src\classes\MergerCLI.java merger\src\merger\*.java
if errorlevel 1 exit /b 1

jar --create --file merger\merger.jar --main-class merger.Main -C merger\out . -C "%STAGE%" .
echo Built merger\merger.jar
