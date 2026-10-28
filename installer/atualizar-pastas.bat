@echo off
setlocal

rem Sem elevacao de administrador, como o testar.bat: basta poder apagar um arquivo dentro da
rem PASTA_INICIAL. Elevar trocaria o usuario, e o administrador pode nao enxergar a mesma
rem unidade mapeada / compartilhamento que o usuario logado enxerga.

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

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0atualizar-pastas.ps1" -PastaInstalacao "%PASTA_INSTALACAO%" %*
set CODIGO=%errorlevel%

echo.
if "%CODIGO%"=="0" (
    echo OK - o robo ja esta usando a lista de pastas atualizada.
)
if "%CODIGO%"=="3" (
    echo ATENCAO - leia a mensagem acima antes de fechar.
)

pause
exit /b %CODIGO%
