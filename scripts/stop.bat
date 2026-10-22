@echo off
chcp 65001 >nul
echo 正在停止占用 8080 / 8001 端口的服务...
powershell -NoProfile -Command "foreach($port in 8080,8001){ $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue; foreach($c in $conns){ try { Stop-Process -Id $c.OwningProcess -Force -ErrorAction Stop; Write-Host ('已停止端口 ' + $port + ' 对应进程 ' + $c.OwningProcess) } catch { Write-Host ('无法停止端口 ' + $port) } } }"
echo 完成。
pause
