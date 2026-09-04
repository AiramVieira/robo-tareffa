param(
    [Parameter(Mandatory = $true)]
    [string]$PastaInstalacao,

    [switch]$Verboso
)

$ErrorActionPreference = "Stop"

# Protege contra o classico bug de "%~dp0" (sempre termina com "\") passado entre aspas:
# a sequencia \" no fim da linha de comando e interpretada como uma aspa literal escapada,
# entao o valor recebido aqui pode vir com uma aspa e/ou barra invertida sobrando no final.
$PastaInstalacao = $PastaInstalacao.TrimEnd('"').TrimEnd('\')

function Resolver-Unc {
    <#
        Mesma logica de Resolver-CaminhoServico do install.ps1, aplicada aqui a PASTA_INICIAL
        (o install.ps1 so a aplica a pasta de INSTALACAO). Duplicada de proposito: um modulo .ps1
        compartilhado complicaria a include-list do pacote em target\dist, e o custo de manter
        estas 10 linhas em dois lugares e menor. Se mudar aqui, veja install.ps1.

        Um Windows Service nao enxerga unidades de rede mapeadas (W:\...), mesmo rodando sob uma
        conta de dominio. Este teste roda como o USUARIO, entao um caminho W:\ pode funcionar aqui
        e falhar no servico - por isso sugerimos o UNC equivalente.
    #>
    param([string]$Caminho)

    $caminhoLimpo = $Caminho.TrimEnd('\')
    if ($caminhoLimpo.StartsWith("\\")) { return $null }
    if ($caminhoLimpo.Length -lt 2 -or $caminhoLimpo[1] -ne ':') { return $null }

    $letra = $caminhoLimpo.Substring(0, 2)
    try {
        $disco = Get-CimInstance -ClassName Win32_LogicalDisk -Filter "DeviceID='$letra'" -ErrorAction Stop
    } catch {
        return $null
    }

    if ($disco -and $disco.DriveType -eq 4 -and $disco.ProviderName) {
        $resto = $caminhoLimpo.Substring(2)
        return ($disco.ProviderName.TrimEnd('\')) + $resto
    }
    return $null
}

Write-Host "=== Teste de pastas do Robo Tareffa ==="
Write-Host "Este teste NAO move, renomeia nem envia nenhum arquivo."
Write-Host ""

# O teste so funciona a partir do PACOTE MONTADO (target\dist, ou a copia dele na maquina do
# cliente), pelos mesmos motivos do install.ps1: fora dele nao existe runtime\bin\java.exe.
$obrigatorios = @('robo.jar', 'runtime\bin\java.exe', 'parametros.txt')
$faltando = @($obrigatorios | Where-Object {
    -not (Test-Path -LiteralPath (Join-Path $PastaInstalacao $_))
})

if ($faltando.Count -gt 0) {
    Write-Host "ERRO: '$PastaInstalacao' nao esta pronto para o teste." -ForegroundColor Red
    foreach ($f in $faltando) { Write-Host "  FALTANDO: $f" -ForegroundColor Red }
    Write-Host ""
    if ($faltando -contains 'parametros.txt') {
        Write-Host "Preencha o parametros.txt primeiro - veja o Passo 2 do GUIA_INSTALACAO.md." -ForegroundColor Yellow
    }
    if ($faltando -contains 'robo.jar' -or $faltando -contains 'runtime\bin\java.exe') {
        Write-Host "Rode testar.bat de DENTRO do pacote montado, nao da pasta-fonte installer\." -ForegroundColor Yellow
    }
    exit 2
}

# --- Unidade mapeada em PASTA_INICIAL: o Java nao consegue resolver o UNC, o PowerShell sim ---
$linhaPastaInicial = Select-String -Path (Join-Path $PastaInstalacao "parametros.txt") `
    -Pattern '^\s*PASTA_INICIAL\s*=\s*(.+?)\s*$' -ErrorAction SilentlyContinue |
    Select-Object -First 1

if ($linhaPastaInicial) {
    $pastaInicial = $linhaPastaInicial.Matches[0].Groups[1].Value
    $unc = Resolver-Unc -Caminho ($pastaInicial.Replace('/', '\'))
    if ($unc) {
        Write-Host "ATENCAO: PASTA_INICIAL usa a unidade mapeada $($pastaInicial.Substring(0,2))." -ForegroundColor Yellow
        Write-Host "         Um servico do Windows NAO enxerga unidades mapeadas. Troque por:" -ForegroundColor Yellow
        Write-Host "         PASTA_INICIAL=$($unc.Replace('\','/'))" -ForegroundColor Yellow
        Write-Host ""
    }
}

$java = Join-Path $PastaInstalacao "runtime\bin\java.exe"
$jar = Join-Path $PastaInstalacao "robo.jar"

$argumentos = @('-jar', $jar, '--testar-pastas')
if ($Verboso) { $argumentos += '--verbose' }

$pastaLogs = Join-Path $PastaInstalacao "logs"
if (-not (Test-Path $pastaLogs)) { New-Item -ItemType Directory -Path $pastaLogs | Out-Null }
$relatorio = Join-Path $pastaLogs ("teste-pastas-" + (Get-Date -Format "yyyy-MM-dd_HHmmss") + ".txt")

# E o SCRIPT que grava o relatorio, nunca o Java: a invariante "o modo de teste nao escreve nada"
# vale para o processo que varre a arvore do cliente. Este arquivo e o que o suporte envia.
& $java @argumentos 2>&1 | Tee-Object -FilePath $relatorio
$codigo = $LASTEXITCODE

Write-Host ""
Write-Host "Relatorio salvo em: $relatorio"
Write-Host ""
Write-Host "Lembre-se: este teste rodou como VOCE ($env:USERNAME), nao como a conta do servico." -ForegroundColor Cyan
Write-Host "Um caminho como W:/ pode funcionar aqui e falhar quando o servico rodar." -ForegroundColor Cyan

if ($codigo -ne 0) {
    Write-Host ""
    Write-Host "Envie o arquivo acima para a Ottimizza se nao souber como resolver." -ForegroundColor Yellow
}

exit $codigo
