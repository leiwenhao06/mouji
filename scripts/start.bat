@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0\.."

if not exist logs mkdir logs
if not exist data\images mkdir data\images

if "%DB_PASSWORD%"=="" (
  set /p DB_PASSWORD=请输入 MySQL root 密码:
)

if not exist frontend\dist\index.html (
  echo [1/4] 未找到前端构建产物，正在构建前端（需要 Node.js）...
  pushd frontend
  if exist node_modules (
    call npm run build
  ) else (
    call npm install
    if errorlevel 1 (
      echo [ERROR] 前端依赖安装失败，请检查 Node.js 与网络。
      popd
      pause
      exit /b 1
    )
    call npm run build
  )
  popd
  if not exist frontend\dist\index.html (
    echo [ERROR] 前端构建失败，请检查 Node.js 版本（建议 18+）。
    pause
    exit /b 1
  )
)

if not exist target\eye-followup-ledger.jar (
  echo [2/4] 正在构建后端...
  call mvn -q -DskipTests package
  if errorlevel 1 (
    echo [ERROR] 后端构建失败，请检查 Maven 与 JDK 17。
    pause
    exit /b 1
  )
)

if exist .venv\Scripts\python.exe (
  echo [3/4] 启动 OCR 服务（8001）...
  start "mouji-ocr" /min cmd /c ".venv\Scripts\python.exe ocr_service\server.py > logs\ocr.log 2>&1"
) else (
  echo [3/4] 未找到 .venv，跳过 OCR 服务；报告导入将不可用（可手动录入）。
)

echo [4/4] 启动后端（8080）...
start "mouji-backend" /min cmd /c "java -jar target\eye-followup-ledger.jar > logs\backend.log 2>&1"

echo 等待服务就绪...
timeout /t 12 >nul
start http://127.0.0.1:8080
echo.
echo 已启动：http://127.0.0.1:8080
echo 默认账号：admin / Admin@2026
echo 日志目录：logs\
endlocal
