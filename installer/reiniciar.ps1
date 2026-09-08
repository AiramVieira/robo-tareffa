param(
    [Parameter(Mandatory = $true)]
    [string]$PastaInstalacao,

    # Por padrao o cache de pastas-alvo e apagado para forcar um remapeamento imediato.
    # Use -ManterCache para reiniciar sem refazer a varredura da arvore de pastas.
    [switch]$ManterCache
)

$ErrorActionPreference = "Stop"

# Mesma protecao do install.ps1: "%~dp0" entre aspas pode chegar aqui com uma aspa literal
# grudada no fim, porque a barra final escapa a aspa de fechamento.
$PastaInstalacao = $PastaInstalacao.TrimEnd('"').TrimEnd('\')

Write-Host "=== Reinicio do Robo Tareffa (Guias de Contas Pagas) ==="
Write-Host "Pasta de instalacao: $PastaInstalacao"

$winswExe = Join-Path $PastaInstalacao "winsw.exe"
if (-not (Test-Path -LiteralPath $winswExe)) {
    Write-Host ""
    Write-Host "ERRO: winsw.exe nao encontrado em '$PastaInstalacao'." -ForegroundColor Red
    Write-Host "Rode este script de DENTRO da pasta do robo (a que tem robo.jar e winsw.exe)." -ForegroundColor Yellow
    exit 1
}

# winsw.xml e o que separa um PACOTE (target\dist recem-montado, ou a copia dele) de uma
# INSTALACAO: ele nao e versionado nem empacotado, e gerado pelo install.ps1 com os caminhos
# absolutos desta maquina. Sem esta checagem, rodar o script de dentro do pacote chega ate o
# winsw.exe e devolve uma excecao .NET crua ("Unable to locate winsw.[xml|yml] file"), que nao
# diz a unica coisa que importa: voce esta na pasta errada.
$winswXml = Join-Path $PastaInstalacao "winsw.xml"
if (-not (Test-Path -LiteralPath $winswXml)) {
    Write-Host ""
    Write-Host "ERRO: '$PastaInstalacao' e um PACOTE, nao uma instalacao - falta winsw.xml." -ForegroundColor Red
    Write-Host ""
    Write-Host "O winsw.xml e criado pelo install.bat, com os caminhos desta maquina. Escolha um:" -ForegroundColor Yellow
    Write-Host "  - Ja existe uma instalacao: rode este script LA. Para descobrir onde ela esta:" -ForegroundColor Yellow
    Write-Host "        sc qc RoboTareffa      (olhe BINARY_PATH_NAME)" -ForegroundColor Yellow
    Write-Host "  - Ainda nao instalou: copie esta pasta para um lugar definitivo e rode install.bat la." -ForegroundColor Yellow
    exit 1
}

# winsw.xml.template copiado a mao para winsw.xml (em vez de gerado pelo install.ps1) deixa os
# marcadores {{...}} intactos. O WinSW aceita esse XML, registra e "inicia" o servico com sucesso -
# e so entao tenta executar um programa chamado literalmente {{EXECUTABLE}}, falhando com
# Win32Exception (2) dentro de uma pasta de log chamada {{LOGPATH}}. Ou seja: o erro aparece longe
# da causa, num log que ninguem encontra porque o proprio caminho do log e um marcador.
$conteudoXml = Get-Content -LiteralPath $winswXml -Raw
if ($conteudoXml -match '\{\{[A-Z_]+\}\}') {
    Write-Host ""
    Write-Host "ERRO: winsw.xml ainda tem marcadores {{...}} por substituir." -ForegroundColor Red
    Write-Host ""
    Write-Host "Ele foi copiado do winsw.xml.template a mao. Quem preenche os caminhos desta" -ForegroundColor Yellow
    Write-Host "maquina e o install.bat - rode-o como Administrador nesta pasta." -ForegroundColor Yellow
    Write-Host "Pode apagar tambem a pasta '{{LOGPATH}}', se existir: ela e efeito do mesmo erro." -ForegroundColor Yellow
    exit 1
}

# O jlink runtime e o unico item do pacote que o Windows trava enquanto o servico roda: copiar
# por cima com o servico no ar pula java.exe em silencio. O sintoma seria o servico nao subir com
# erro 1053 (timeout do SCM), sem nada util em log nenhum - por isso a checagem e aqui, antes.
$javaExe = Join-Path $PastaInstalacao 'runtime' | Join-Path -ChildPath 'bin' | Join-Path -ChildPath 'java.exe'
if (-not (Test-Path -LiteralPath $javaExe)) {
    Write-Host ""
    Write-Host "ERRO: runtime\bin\java.exe nao encontrado em '$PastaInstalacao'." -ForegroundColor Red
    Write-Host "A pasta runtime\ esta incompleta. Pare o servico, copie runtime\ de novo do pacote" -ForegroundColor Yellow
    Write-Host "e so entao reinicie - com o servico no ar o java.exe fica travado e nao e substituido." -ForegroundColor Yellow
    exit 1
}

$servico = Get-Service -Name "RoboTareffa" -ErrorAction SilentlyContinue
if (-not $servico) {
    Write-Host ""
    Write-Host "ERRO: o servico 'RoboTareffa' nao esta instalado nesta maquina." -ForegroundColor Red
    Write-Host "Rode install.bat primeiro - este script so reinicia um servico ja instalado." -ForegroundColor Yellow
    exit 1
}

# --- O que muda sozinho e o que exige reinicio -------------------------------------------
# parametros.txt e mapa-pastas.txt sao relidos a cada ciclo pelo proprio robo, entao alterar
# CONTABILIDADE, PASTA_INICIAL, NIVEIS_SUBPASTA, CUSTOMIZACAO ou o mapa NAO precisa deste
# script - vale em ate 30 segundos.
# As credenciais nao sao mais arquivo: vem cifradas dentro do robo.jar e sao lidas uma unica vez
# no startup. Trocar credencial passou a ser trocar o robo.jar (pacote novo) e reiniciar - o
# reinicio continua sendo este script.

# --- Cache de pastas-alvo ----------------------------------------------------------------
# Quando NIVEIS_SUBPASTA e maior que zero (ou existe mapa-pastas.txt), a lista de pastas-alvo
# fica em cache por INTERVALO_REMAPEAMENTO_HORAS (padrao 24h) dentro da propria PASTA_INICIAL.
#
# O robo ja invalida esse cache sozinho quando a CONFIGURACAO de descoberta muda (o cache
# guarda uma impressao digital dela), entao apagar o cache a mao NAO e mais necessario depois
# de editar CUSTOMIZACAO/SUBNIVEL_ANO/mapa-pastas.txt.
#
# O que este bloco ainda resolve e o caso em que NADA na configuracao mudou, mas a ARVORE
# mudou: uma pasta de cliente criada depois do ultimo mapeamento so seria vista no dia
# seguinte. Apagar o cache forca o remapeamento no proximo ciclo.
# (Com NIVEIS_SUBPASTA=0 e sem mapa, o robo ignora o cache por completo, entao isso vira um
# no-op.)
if (-not $ManterCache) {
    $arquivoParametros = Join-Path $PastaInstalacao "parametros.txt"
    if (Test-Path -LiteralPath $arquivoParametros) {
        $pastaInicial = $null
        foreach ($linha in Get-Content -LiteralPath $arquivoParametros) {
            if ($linha -match '^\s*PASTA_INICIAL\s*=\s*(.+?)\s*$') {
                $pastaInicial = $Matches[1]
            }
        }

        if ($pastaInicial) {
            $arquivoCache = Join-Path $pastaInicial ".robo_pastas_alvo.cache.json"
            if (Test-Path -LiteralPath $arquivoCache) {
                try {
                    Remove-Item -LiteralPath $arquivoCache -Force
                    Write-Host "Cache de pastas-alvo apagado: $arquivoCache"
                    Write-Host "  (o robo refaz a varredura da arvore no proximo ciclo)"
                } catch {
                    Write-Warning "Nao foi possivel apagar o cache em ${arquivoCache}: $($_.Exception.Message)"
                }
            } else {
                Write-Host "Nenhum cache de pastas-alvo para apagar (em $pastaInicial)."
            }
        } else {
            Write-Warning "PASTA_INICIAL nao encontrada em parametros.txt - cache nao foi apagado."
        }
    } else {
        Write-Warning "parametros.txt nao encontrado em $PastaInstalacao - cache nao foi apagado."
    }
}

# --- Reinicio com verificacao ------------------------------------------------------------
$logPath     = Join-Path $PastaInstalacao "logs"
$logWrapper  = Join-Path $logPath "winsw.wrapper.log"
$linhasAntes = 0
if (Test-Path -LiteralPath $logWrapper) {
    try { $linhasAntes = @(Get-Content -LiteralPath $logWrapper).Count } catch { $linhasAntes = 0 }
}

function Get-NovasLinhasLog {
    if (-not (Test-Path -LiteralPath $logWrapper)) { return @() }
    # o WinSW mantem o arquivo aberto para escrita: se der IOException, tenta no proximo ciclo
    try { return @(Get-Content -LiteralPath $logWrapper | Select-Object -Skip $linhasAntes) } catch { return @() }
}

Write-Host ""
Write-Host "Parando o servico..."
& $winswExe stop | Out-Null

# Espera o processo realmente sair antes de subir de novo, senao o start pode pegar o
# servico ainda em StopPending e falhar.
$limiteParada = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $limiteParada) {
    $svc = Get-Service -Name "RoboTareffa" -ErrorAction SilentlyContinue
    if (-not $svc -or $svc.Status -eq 'Stopped') { break }
    Start-Sleep -Seconds 1
}

Write-Host "Iniciando o servico..."
& $winswExe start
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "FALHA: winsw.exe start retornou $LASTEXITCODE." -ForegroundColor Red
    Get-NovasLinhasLog | Select-Object -Last 20 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

# O WinSW responde RUNNING ao SCM ANTES de criar o java.exe filho: 'start' retorna 0 mesmo
# quando o robo morre logo depois. Sem esta verificacao o script mentiria que deu certo.
Write-Host "Verificando se o servico se mantem no ar..."
$limite = (Get-Date).AddSeconds(20)
$status = 'Desconhecido'
$novas  = @()
$erros  = @()

while ($true) {
    Start-Sleep -Seconds 2

    $svc    = Get-Service -Name "RoboTareffa" -ErrorAction SilentlyContinue
    $status = if ($svc) { [string]$svc.Status } else { 'Ausente' }
    $novas  = Get-NovasLinhasLog
    $erros  = @($novas | Where-Object { $_ -match '(?i)\b(ERROR|FATAL)\b' })

    if ($erros.Count -gt 0) { break }
    if ($status -eq 'Stopped' -or $status -eq 'Ausente') { break }
    if ((Get-Date) -ge $limite) { break }
    # 'StartPending' NAO e falha: continua no laco enquanto ainda esta subindo
}

if ($status -ne 'Running' -or $erros.Count -gt 0) {
    Write-Host ""
    Write-Host "FALHA: o servico nao se manteve no ar (status: $status)." -ForegroundColor Red
    if ($novas.Count -gt 0) {
        Write-Host "Ultimas linhas de ${logWrapper}:" -ForegroundColor Red
        $novas | Select-Object -Last 20 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    }
    Write-Host ""
    Write-Host "O servico continua INSTALADO, apenas parado. Corrija a configuracao e rode" -ForegroundColor Yellow
    Write-Host "este script de novo, ou use uninstall.bat para remove-lo." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Servico 'RoboTareffa' reiniciado e RODANDO (verificado)." -ForegroundColor Green

# Mostra o que o robo escreveu logo apos subir, para dar retorno imediato ao operador.
$logSaida = Join-Path $logPath "winsw.out.log"
if (Test-Path -LiteralPath $logSaida) {
    Write-Host ""
    Write-Host "Ultimas linhas de winsw.out.log:"
    try {
        Get-Content -LiteralPath $logSaida -Tail 8 | ForEach-Object { Write-Host "  $_" }
    } catch {
        Write-Host "  (log em uso pelo servico - abra o arquivo manualmente)"
    }
}
