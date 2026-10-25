@echo off
REM SkillLoop - create tables, views and demo data (non-destructive)
cd /d "%~dp0.."
call mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--all"
pause
