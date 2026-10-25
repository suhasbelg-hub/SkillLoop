@echo off
REM SkillLoop - start the desktop application
cd /d "%~dp0.."
call mvn -q exec:java -Dexec.mainClass=com.skillloop.Main
pause
