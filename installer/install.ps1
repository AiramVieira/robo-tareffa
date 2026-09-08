param(
    [Parameter(Mandatory = $true)]
    [string]$PastaInstalacao
)

$ErrorActionPreference = "Stop"

# Protege contra o classico bug de "%~dp0" (sempre termina com "\") passado entre aspas:
# a sequencia \" no fim da linha de comando e interpretada como uma aspa literal escapada,
# entao o valor recebido aqui pode vir com uma aspa e/ou barra invertida sobrando no final.
$PastaInstalacao = $PastaInstalacao.TrimEnd('"').TrimEnd('\')

function Resolver-CaminhoServico {
    <#
        Um Windows Service (mesmo rodando sob uma conta de dominio) nao enxerga unidades de
        rede mapeadas (Z:\...) - so caminhos UNC (\\servidor\pasta) funcionam de forma
        confiavel no contexto de um servico. Se a instalacao estiver numa unidade mapeada,
        resolve e devolve o caminho UNC equivalente; caso contrario devolve o caminho como esta.
    #>
    param([string]$Caminho)

    $caminhoLimpo = $Caminho.TrimEnd('\')
    if ($caminhoLimpo.StartsWith("\\")) {
        return @{ Caminho = $caminhoLimpo; ViaRede = $true }
    }

    $letra = $caminhoLimpo.Substring(0, 2)
    $disco = Get-CimInstance -ClassName Win32_LogicalDisk -Filter "DeviceID='$letra'" -ErrorAction SilentlyContinue

    if ($disco -and $disco.DriveType -eq 4 -and $disco.ProviderName) {
        $resto = $caminhoLimpo.Substring(2)
        $unc = ($disco.ProviderName.TrimEnd('\')) + $resto
        Write-Host "Unidade mapeada detectada ($letra) - usando caminho UNC real para o servico: $unc"
        return @{ Caminho = $unc; ViaRede = $true }
    }

    return @{ Caminho = $caminhoLimpo; ViaRede = $false }
}

function EscaparXml {
    <#
        Escapa os caracteres que quebram um documento XML. Necessario porque caminhos e,
        principalmente, a senha da conta de servico sao interpolados direto no winsw.xml.
    #>
    param([string]$Valor)
    if ($null -eq $Valor) { return "" }
    return $Valor.Replace('&', '&amp;').
                  Replace('<', '&lt;').
                  Replace('>', '&gt;').
                  Replace('"', '&quot;').
                  Replace("'", '&apos;')
}

function SecureStringParaTexto {
    param([System.Security.SecureString]$Valor)
    $ponteiro = [System.Runtime.InteropServices.Marshal]::SecureStringToGlobalAllocUnicode($Valor)
    try {
        return [System.Runtime.InteropServices.Marshal]::PtrToStringUni($ponteiro)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeGlobalAllocUnicode($ponteiro)
    }
}

Write-Host "=== Instalador do Robo Tareffa (Guias de Contas Pagas) ==="
Write-Host "Pasta de instalacao: $PastaInstalacao"

$winswExe = Join-Path $PastaInstalacao "winsw.exe"

# O instalador so funciona a partir do PACOTE MONTADO (target\dist, ou a copia dele na maquina
# do cliente). Rodar de dentro da pasta-fonte installer\ do projeto gera um winsw.xml apontando
# para runtime\bin\java.exe e robo.jar inexistentes - e o servico e registrado assim mesmo,
# falhando so depois, de forma assincrona, com Win32Exception (2) no winsw.wrapper.log.
$obrigatorios = @('winsw.exe', 'winsw.xml.template', 'robo.jar', 'runtime\bin\java.exe')

# parametros.txt e criado a partir do .example logo abaixo: so exija o .example quando o arquivo
# real ainda nao existir (senao uma reinstalacao ja configurada falha). Nao ha mais equivalente
# para credenciais: elas viajam cifradas dentro do robo.jar, sem arquivo na pasta de instalacao.
if (-not (Test-Path -LiteralPath (Join-Path $PastaInstalacao 'parametros.txt'))) {
    $obrigatorios += 'parametros.txt.example'
}

$faltando = @($obrigatorios | Where-Object {
    -not (Test-Path -LiteralPath (Join-Path $PastaInstalacao $_))
})

if ($faltando.Count -gt 0) {
    Write-Host ""
    Write-Host "ERRO: '$PastaInstalacao' nao e um pacote de instalacao completo." -ForegroundColor Red
    foreach ($f in $faltando) { Write-Host "  FALTANDO: $f" -ForegroundColor Red }
    Write-Host ""
    if ($faltando -contains 'winsw.exe') {
        Write-Host "winsw.exe nao e versionado - veja winsw\README.md para saber onde baixa-lo." -ForegroundColor Yellow
    }
    Write-Host "Rode install.bat de DENTRO do pacote montado (target\dist, ou a copia dele no cliente)," -ForegroundColor Yellow
    Write-Host "nao da pasta-fonte installer\ do projeto." -ForegroundColor Yellow
    Write-Host "Para gerar o pacote: mvnw.cmd clean package" -ForegroundColor Yellow
    exit 1
}

$resolucao = Resolver-CaminhoServico -Caminho $PastaInstalacao
$caminhoServico = $resolucao.Caminho
$instalacaoEmRede = $resolucao.ViaRede

# --- Arquivos de configuracao: cria a partir do .example se ainda nao existirem ---
$parametros = Join-Path $PastaInstalacao "parametros.txt"
if (-not (Test-Path $parametros)) {
    Copy-Item (Join-Path $PastaInstalacao "parametros.txt.example") $parametros
    Write-Warning "parametros.txt criado a partir do exemplo - edite CONTABILIDADE/PASTA_INICIAL/NIVEIS_SUBPASTA antes de usar em producao."
}

# --- Bloco <serviceaccount>, somente quando a instalacao esta em compartilhamento de rede ---
$blocoConta = ""
if ($instalacaoEmRede) {
    Write-Host ""
    Write-Host "A instalacao esta em um compartilhamento de rede."
    Write-Host "O servico do Windows precisa rodar sob uma conta com permissao de leitura/escrita nesse compartilhamento"
    Write-Host "(a conta padrao LocalSystem nao consegue acessar compartilhamentos de outros servidores)."
    $usuario = Read-Host "Informe o usuario (DOMINIO\usuario ou .\usuario)"
    $senhaSegura = Read-Host "Informe a senha" -AsSecureString
    $senha = SecureStringParaTexto -Valor $senhaSegura

    $dominio = ".\"
    $nomeUsuario = $usuario
    if ($usuario.Contains("\")) {
        $partes = $usuario.Split("\", 2)
        $dominio = $partes[0]
        $nomeUsuario = $partes[1]
    }

    # Senha/usuario vao para dentro de um XML: sem escape, um & ou < gera winsw.xml malformado
    # e o WinSW nao consegue nem instalar nem iniciar o servico.
    $blocoConta = @"
  <serviceaccount>
    <domain>$(EscaparXml $dominio)</domain>
    <user>$(EscaparXml $nomeUsuario)</user>
    <password>$(EscaparXml $senha)</password>
    <allowservicelogon>true</allowservicelogon>
  </serviceaccount>
"@
}

# --- Gera winsw.xml a partir do template ---
$executavel = Join-Path $caminhoServico "runtime\bin\java.exe"
$argumentos = "-jar `"$(Join-Path $caminhoServico 'robo.jar')`""
$logPath = Join-Path $caminhoServico "logs"

$template = Get-Content (Join-Path $PastaInstalacao "winsw.xml.template") -Raw
$xml = $template.Replace("{{EXECUTABLE}}", (EscaparXml $executavel)).
                 Replace("{{ARGUMENTS}}", (EscaparXml $argumentos)).
                 Replace("{{LOGPATH}}", (EscaparXml $logPath)).
                 Replace("{{SERVICEACCOUNT_BLOCK}}", $blocoConta)

# O WinSW exige que o arquivo de configuracao tenha o MESMO nome-base do executavel
# (winsw.exe -> winsw.xml), independente do <id>/<name> configurados dentro dele.
$arquivoServicoXml = Join-Path $PastaInstalacao "winsw.xml"
Set-Content -Path $arquivoServicoXml -Value $xml -Encoding UTF8

# --- Reinstala de forma idempotente: para/remove um servico existente antes de reinstalar ---
$servicoExistente = Get-Service -Name "RoboTareffa" -ErrorAction SilentlyContinue
if ($servicoExistente) {
    Write-Host "Servico ja existente - parando e removendo antes de reinstalar..."
    & $winswExe stop | Out-Null
    & $winswExe uninstall | Out-Null
    Start-Sleep -Seconds 2
}

Write-Host "Instalando o servico via WinSW..."
& $winswExe install
if ($LASTEXITCODE -ne 0) {
    Write-Error "Falha ao instalar o servico (winsw.exe install retornou $LASTEXITCODE)."
    exit 1
}

Write-Host "Iniciando o servico..."

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

function Abortar-Instalacao {
    param([string]$Motivo, [string[]]$Linhas = @())
    Write-Host ""
    Write-Host "FALHA: $Motivo" -ForegroundColor Red
    if ($Linhas.Count -gt 0) {
        Write-Host "Ultimas linhas de ${logWrapper}:" -ForegroundColor Red
        $Linhas | Select-Object -Last 20 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    }
    Write-Host "Removendo o servico para nao deixar um servico quebrado com restart automatico..." -ForegroundColor Yellow
    & $winswExe stop      | Out-Null
    & $winswExe uninstall | Out-Null
    exit 1
}

& $winswExe start
if ($LASTEXITCODE -ne 0) {
    Abortar-Instalacao "winsw.exe start retornou $LASTEXITCODE." (Get-NovasLinhasLog)
}

# O WinSW responde RUNNING ao SCM ANTES de criar o java.exe filho: 'start' retorna 0 e o processo
# do robo pode morrer ~200ms depois (foi o que aconteceu com o Win32Exception 2 quando
# runtime\bin\java.exe nao existia). Sem esta verificacao o instalador declara sucesso em verde
# enquanto o servico esta morto - por isso ela e obrigatoria.
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

    if ($erros.Count -gt 0) { break }                              # falha explicita no log
    if ($status -eq 'Stopped' -or $status -eq 'Ausente') { break } # morreu
    if ((Get-Date) -ge $limite) { break }                          # janela cumprida
    # 'StartPending' NAO e falha: continua no laco enquanto ainda esta subindo
}

if ($status -ne 'Running' -or $erros.Count -gt 0) {
    Abortar-Instalacao "o servico iniciou mas nao se manteve no ar (status: $status)." $novas
}

if ($instalacaoEmRede) {
    Write-Host ""
    Write-Host "Observacao: apos confirmar que o servico esta rodando normalmente, voce pode remover" -ForegroundColor Yellow
    Write-Host "a linha <password> de winsw.xml - o Windows ja armazena a credencial de logon" -ForegroundColor Yellow
    Write-Host "do servico de forma criptografada e nao precisa mais do texto em claro no arquivo." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Servico 'RoboTareffa' instalado e RODANDO (verificado)." -ForegroundColor Green
