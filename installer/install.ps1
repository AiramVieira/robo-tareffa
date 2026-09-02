param(
    [Parameter(Mandatory = $true)]
    [string]$PastaInstalacao
)

$ErrorActionPreference = "Stop"

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
if (-not (Test-Path $winswExe)) {
    Write-Error "winsw.exe nao encontrado em $PastaInstalacao. Veja winsw\README.md para saber onde baixa-lo."
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

$credenciais = Join-Path $PastaInstalacao "credenciais.properties"
if (-not (Test-Path $credenciais)) {
    Copy-Item (Join-Path $PastaInstalacao "credenciais.properties.example") $credenciais
    Write-Warning "credenciais.properties criado a partir do exemplo - preencha AUTH_SERVER_URL/CLIENT_ID/CLIENT_SECRET/SENHA_INTEGRACAO antes de usar em producao."
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

    $blocoConta = @"
  <serviceaccount>
    <domain>$dominio</domain>
    <user>$nomeUsuario</user>
    <password>$senha</password>
    <allowservicelogon>true</allowservicelogon>
  </serviceaccount>
"@
}

# --- Gera robo-service.xml a partir do template ---
$executavel = Join-Path $caminhoServico "runtime\bin\java.exe"
$argumentos = "-jar `"$(Join-Path $caminhoServico 'robo.jar')`""
$logPath = Join-Path $caminhoServico "logs"

$template = Get-Content (Join-Path $PastaInstalacao "winsw.xml.template") -Raw
$xml = $template.Replace("{{EXECUTABLE}}", $executavel).
                 Replace("{{ARGUMENTS}}", $argumentos).
                 Replace("{{LOGPATH}}", $logPath).
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
& $winswExe start
if ($LASTEXITCODE -ne 0) {
    Write-Error "Falha ao iniciar o servico (winsw.exe start retornou $LASTEXITCODE)."
    exit 1
}

if ($instalacaoEmRede) {
    Write-Host ""
    Write-Host "Observacao: apos confirmar que o servico esta rodando normalmente, voce pode remover" -ForegroundColor Yellow
    Write-Host "a linha <password> de robo-service.xml - o Windows ja armazena a credencial de logon" -ForegroundColor Yellow
    Write-Host "do servico de forma criptografada e nao precisa mais do texto em claro no arquivo." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Servico 'RoboTareffa' instalado e iniciado." -ForegroundColor Green
