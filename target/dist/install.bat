@echo off
setlocal

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Solicitando elevacao de administrador...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install.ps1" -PastaInstalacao "%~dp0"
if %errorlevel% neq 0 (
    echo.
    echo Falha na instalacao - veja as mensagens acima.
    pause
    exit /b 1
)

echo.
echo Robo instalado e iniciado com sucesso.
pause
