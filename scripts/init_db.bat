@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0\.."
if "%DB_PASSWORD%"=="" (
  set /p DB_PASSWORD=请输入 MySQL root 密码:
)
set MYSQL_PWD=%DB_PASSWORD%
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u root < sql\init.sql
if errorlevel 1 (
  echo [ERROR] 数据库初始化失败，请确认 MySQL 已启动且密码正确。
) else (
  echo 数据库 eye_followup_ledger 初始化完成。
)
set MYSQL_PWD=
pause
endlocal
