@echo off
REM SkillLoop - unit tests (add " db-tests" as a parameter for the DB suite)
cd /d "%~dp0.."
if "%1"=="" (
  call mvn test
) else (
  call mvn test -P db-tests
)
pause
