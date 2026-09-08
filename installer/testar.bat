@echo off
setlocal

rem Sem elevacao de administrador, ao contrario de install.bat: este teste e somente-leitura e
rem nao precisa. Mais importante: elevar trocaria o usuario, e isso ESCONDERIA justamente o
rem problema de permissao / unidade mapeada que este teste existe para revelar.

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

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0testar.ps1" -PastaInstalacao "%PASTA_INSTALACAO%" %*
set CODIGO=%errorlevel%

echo.
if "%CODIGO%"=="0" (
    echo OK - as pastas listadas acima sao as que o robo vai monitorar.
)
if "%CODIGO%"=="2" (
    echo ATENCAO - a configuracao esta invalida. Corrija o que foi apontado acima.
)
if "%CODIGO%"=="3" (
    echo ATENCAO - nenhuma pasta encontrada. O robo nao enviaria nenhum arquivo.
)

pause
exit /b %CODIGO%
