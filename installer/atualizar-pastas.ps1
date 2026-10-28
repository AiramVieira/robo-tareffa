param(
    [Parameter(Mandatory = $true)]
    [string]$PastaInstalacao
)

$ErrorActionPreference = "Stop"

# Mesma protecao do install.ps1: "%~dp0" entre aspas pode chegar aqui com uma aspa literal
# grudada no fim, porque a barra final escapa a aspa de fechamento.
$PastaInstalacao = $PastaInstalacao.TrimEnd('"').TrimEnd('\')

# --- Para que serve ------------------------------------------------------------------------
# O robo nao varre a arvore de pastas do cliente a cada ciclo: ele guarda a lista de
# pastas-alvo num cache (.robo_pastas_alvo.cache.json, dentro da PASTA_INICIAL) e so refaz a
# varredura completa a cada INTERVALO_REMAPEAMENTO_HORAS (padrao 1h). E isso que evita pesar
# no computador / servidor do cliente.
#
# O efeito colateral: uma pasta de cliente criada agora so seria vista no proximo remapeamento.
# Este script antecipa esse remapeamento: apaga o cache, e o proprio SERVICO refaz a varredura
# no ciclo seguinte (segundos) e grava um cache novo. Como o cache novo nasce com a data de
# agora, o contador do intervalo (1h por padrao) recomeca a partir deste momento.
#
# Quem gera o cache novo e o servico, nunca este script: o servico pode rodar com outra conta
# e enxergar a arvore de outro jeito (unidade mapeada, permissoes). Um cache gerado aqui, como
# o usuario logado, poderia listar pastas que o servico nao alcanca.
#
# Nao precisa deste script depois de editar parametros.txt ou mapa-pastas.txt: o robo percebe
# a mudanca de configuracao sozinho e remapeia no ciclo seguinte (secao 6.2 do readme).
# -------------------------------------------------------------------------------------------

$nomeServico = "RoboTareffa"
$nomeCache = ".robo_pastas_alvo.cache.json"

Write-Host "=== Atualizar lista de pastas do Robo Tareffa ==="
Write-Host ""

$arquivoParametros = Join-Path $PastaInstalacao "parametros.txt"
if (-not (Test-Path -LiteralPath $arquivoParametros)) {
    Write-Host "ERRO: parametros.txt nao encontrado em '$PastaInstalacao'." -ForegroundColor Red
    Write-Host "Rode este script de DENTRO da pasta do robo (a que tem robo.jar e parametros.txt)." -ForegroundColor Yellow
    exit 1
}

function Ler-Chave {
    param([string]$Chave)
    $valor = $null
    foreach ($linha in Get-Content -LiteralPath $arquivoParametros) {
        if ($linha -match ('^\s*' + $Chave + '\s*=\s*(.*?)\s*$')) {
            $valor = $Matches[1]
        }
    }
    return $valor
}

$pastaInicial = Ler-Chave "PASTA_INICIAL"
if (-not $pastaInicial) {
    Write-Host "ERRO: PASTA_INICIAL nao esta preenchida no parametros.txt." -ForegroundColor Red
    exit 1
}

# Com NIVEIS_SUBPASTA=0 e sem mapa-pastas.txt o robo monitora so a PASTA_INICIAL e nao usa
# cache nenhum - nao ha lista para atualizar.
$niveis = Ler-Chave "NIVEIS_SUBPASTA"
$temMapa = Test-Path -LiteralPath (Join-Path $PastaInstalacao "mapa-pastas.txt")
if (-not $temMapa -and $niveis -eq "0") {
    Write-Host "Nada a fazer: com NIVEIS_SUBPASTA=0 (e sem mapa-pastas.txt) o robo monitora apenas"
    Write-Host "a propria PASTA_INICIAL e nao guarda lista de pastas em cache."
    exit 0
}

$intervaloVarredura = 30
$textoIntervalo = Ler-Chave "INTERVALO_VARREDURA_SEGUNDOS"
if ($textoIntervalo -match '^\d+$' -and [int]$textoIntervalo -gt 0) {
    $intervaloVarredura = [int]$textoIntervalo
}

if (-not (Test-Path -LiteralPath $pastaInicial)) {
    Write-Host "ERRO: nao foi possivel acessar a PASTA_INICIAL '$pastaInicial'." -ForegroundColor Red
    Write-Host "Rode o testar.bat para ver o diagnostico completo." -ForegroundColor Yellow
    exit 1
}

$arquivoCache = Join-Path $pastaInicial $nomeCache

# --- 1. Apagar o cache --------------------------------------------------------------------
if (Test-Path -LiteralPath $arquivoCache) {
    try {
        Remove-Item -LiteralPath $arquivoCache -Force
    } catch {
        Write-Host "ERRO: nao foi possivel apagar o cache em ${arquivoCache}:" -ForegroundColor Red
        Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
    Write-Host "Cache de pastas apagado: $arquivoCache"
} else {
    Write-Host "Nao havia cache de pastas em $pastaInicial (o robo vai criar um agora)."
}

# --- 2. Esperar o servico gerar o cache novo ------------------------------------------------
$servico = Get-Service -Name $nomeServico -ErrorAction SilentlyContinue
if (-not $servico -or $servico.Status -ne 'Running') {
    Write-Host ""
    Write-Host "O servico '$nomeServico' nao esta rodando agora." -ForegroundColor Yellow
    Write-Host "A lista de pastas sera refeita assim que ele for iniciado." -ForegroundColor Yellow
    exit 3
}

# O robo termina o ciclo em andamento, espera INTERVALO_VARREDURA_SEGUNDOS e so entao remapeia.
# A varredura em si pode levar minutos num compartilhamento de rede grande, dai a folga.
$limite = (Get-Date).AddSeconds($intervaloVarredura + 600)
Write-Host ""
Write-Host "Aguardando o robo refazer a varredura das pastas (pode levar alguns minutos)" -NoNewline

$dados = $null
while ((Get-Date) -lt $limite) {
    Start-Sleep -Seconds 2
    Write-Host "." -NoNewline
    if (-not (Test-Path -LiteralPath $arquivoCache)) { continue }
    # O robo pode estar no meio da gravacao: JSON incompleto e so tentar de novo.
    try {
        $dados = Get-Content -LiteralPath $arquivoCache -Raw | ConvertFrom-Json
        if ($dados -and $dados.geradoEm) { break }
    } catch {
        $dados = $null
    }
}
Write-Host ""

if (-not $dados) {
    Write-Host ""
    Write-Host "O robo ainda nao terminou a varredura. Isso e normal em arvores de pastas grandes:" -ForegroundColor Yellow
    Write-Host "o cache ja foi apagado e a lista nova vai ser gravada assim que a varredura acabar." -ForegroundColor Yellow
    Write-Host "Acompanhe em: $(Join-Path $PastaInstalacao 'logs\winsw.out.log')" -ForegroundColor Yellow
    exit 3
}

$quantidade = @($dados.pastas).Count
$geradoEm = $dados.geradoEm
try { $geradoEm = ([DateTimeOffset]::Parse([string]$dados.geradoEm)).LocalDateTime.ToString("dd/MM/yyyy HH:mm:ss") } catch {}

$intervaloRemap = 1
$textoRemap = Ler-Chave "INTERVALO_REMAPEAMENTO_HORAS"
if ($textoRemap -match '^\d+$' -and [int]$textoRemap -gt 0) { $intervaloRemap = [int]$textoRemap }

Write-Host ""
Write-Host "Lista de pastas atualizada em $geradoEm - $quantidade pasta(s) monitorada(s)." -ForegroundColor Green
Write-Host "A proxima atualizacao automatica sera daqui a $intervaloRemap hora(s)."
if ($quantidade -eq 0) {
    Write-Host ""
    Write-Host "ATENCAO: nenhuma pasta encontrada. Rode o testar.bat para ver o motivo." -ForegroundColor Yellow
    exit 3
}
exit 0
