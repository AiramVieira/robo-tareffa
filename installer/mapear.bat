@echo off
setlocal

rem Abre no navegador o mapeador de pastas (secao 6.8 do readme): uma tela para montar o
rem mapa-pastas.txt clicando nas pastas do cliente, com a conferencia feita pelo proprio robo.
rem
rem Sem elevacao de administrador, como o testar.bat: o mapeador mostra as pastas que o USUARIO
rem LOGADO enxerga, e elevar trocaria o usuario - escondendo justamente o problema de permissao /
rem unidade mapeada que o analista precisa ver.
rem
rem Esta janela fica aberta enquanto o mapeador roda: fecha-la encerra o mapeador.
rem
rem "mapear.bat --sem-navegador" so mostra o endereco, sem abrir o navegador padrao - para quando
rem ele e o Internet Explorer (servidor antigo) e a tela precisa ser aberta no Edge ou no Chrome.

set "JAVA=%~dp0runtime\bin\java.exe"
set "JAR=%~dp0robo.jar"

if not exist "%JAVA%" goto :pacote_incompleto
if not exist "%JAR%" goto :pacote_incompleto

title Mapeador de pastas - Robo Tareffa
"%JAVA%" -jar "%JAR%" --mapear %*
set CODIGO=%errorlevel%

echo.
if "%CODIGO%"=="2" (
    echo ATENCAO - corrija o que foi apontado acima e rode o mapear.bat de novo.
)
pause
exit /b %CODIGO%

:pacote_incompleto
echo ERRO: esta pasta nao tem o robo.jar e o runtime\bin\java.exe.
echo Rode o mapear.bat de DENTRO da pasta do robo (o pacote montado), nao da pasta-fonte installer\.
pause
exit /b 2
