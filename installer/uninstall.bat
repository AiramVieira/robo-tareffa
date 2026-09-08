@echo off
setlocal

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Solicitando elevacao de administrador...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

set WINSW=%~dp0winsw.exe
if not exist "%WINSW%" (
    echo winsw.exe nao encontrado em %~dp0
    pause
    exit /b 1
)

echo Parando e removendo o servico RoboTareffa...
rem 'stop' falha quando o servico ja esta parado - isso e esperado, nao e erro.
"%WINSW%" stop
"%WINSW%" uninstall
if %errorlevel% neq 0 (
    echo.
    echo FALHA ao remover o servico ^(winsw uninstall retornou %errorlevel%^).
    echo Confira se esta janela foi aberta como Administrador e veja logs\winsw.wrapper.log.
    pause
    exit /b 1
)

echo.
echo Servico removido.
pause
