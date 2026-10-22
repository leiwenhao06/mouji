@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0\.."

if not exist logs mkdir logs
if not exist data\images mkdir data\images

if "%DB_PASSWORD%"=="" (
  set /p DB_PASSWORD=请输入 MySQL root 密码:
)

if not exist target\eye-followup-ledger.jar (
  echo [1/3] 正在构建后端...
  call mvn -q -DskipTests package
  if errorlevel 1 (
    echo [ERROR] 后端构建失败，请检查 Maven 与 JDK 17。
    pause
    exit /b 1
  )
)

if exist .venv\Scripts\python.exe (
  echo [2/3] 启动 OCR 服务（8001）...
  start "mouji-ocr" /min cmd /c ".venv\Scripts\python.exe ocr_service\server.py > logs\ocr.log 2>&1"
) else (
  echo [2/3] 未找到 .venv，跳过 OCR 服务；报告导入将不可用（可手动录入）。
)

echo [3/3] 启动后端（8080）...
start "mouji-backend" /min cmd /c "java -jar target\eye-followup-ledger.jar > logs\backend.log 2>&1"

echo 等待服务就绪...
timeout /t 12 >nul
start http://127.0.0.1:8080
echo.
echo 已启动：http://127.0.0.1:8080
echo 默认账号：admin / Admin@2026
echo 日志目录：logs\
endlocal
