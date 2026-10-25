@echo off
REM SkillLoop - create the first administrator (interactive, hidden password)
cd /d "%~dp0.."
call mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.AdminBootstrapTool
pause
