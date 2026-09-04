@echo off
setlocal

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Solicitando elevacao de administrador...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

rem %~dp0 sempre termina com "\". Passar "...\" entre aspas faria a barra escapar a aspa de
rem fechamento (regra de parsing do Windows), e o PowerShell receberia o caminho com uma aspa
rem literal grudada no fim. Por isso a barra final e removida antes de passar o argumento.
rem Excecao: numa raiz de disco ("D:\") remover a barra deixaria "D:", que e um caminho
rem relativo ao drive, e nao absoluto - nesse caso a barra e mantida e o \ e duplicado.
set "PASTA_INSTALACAO=%~dp0"
if "%PASTA_INSTALACAO:~-2%"==":\" (
    set "PASTA_INSTALACAO=%PASTA_INSTALACAO%\"
) else (
    set "PASTA_INSTALACAO=%PASTA_INSTALACAO:~0,-1%"
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0reiniciar.ps1" -PastaInstalacao "%PASTA_INSTALACAO%"
if %errorlevel% neq 0 (
    echo.
    echo Falha ao reiniciar - veja as mensagens acima.
    pause
    exit /b 1
)

echo.
echo Robo reiniciado com sucesso.
pause
