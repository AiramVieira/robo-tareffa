# WinSW - wrapper de servico do Windows

O robô roda como um Windows Service usando o [WinSW](https://github.com/winsw/winsw)
(licença MIT). O binário **não é versionado neste repositório** - baixe antes de
distribuir o pacote (pasta `target/dist` gerada pelo build) para uma máquina de
cliente ou para o compartilhamento de rede.

## Passo único de preparação (antes de cada distribuição)

1. Baixe `WinSW-x64.exe` da [página de releases do WinSW](https://github.com/winsw/winsw/releases)
   (recomendado: a versão mais recente da série 2.x, estável e amplamente usada).
2. Renomeie o arquivo para `winsw.exe`.
3. Coloque `winsw.exe` dentro de `target/dist/` (junto de `robo.jar`, `install.bat` etc.)
   antes de copiar essa pasta para a máquina do cliente ou para o compartilhamento de rede.

O `install.bat`/`install.ps1` verifica a presença de `winsw.exe` e falha com uma mensagem
clara caso ele não esteja lá.

## Onde instalar

`target/dist/` é uma pasta autocontida (jar + runtime Java embutido via `jlink` + WinSW +
scripts de instalação) e pode ser colocada em dois lugares:

- **Localmente**, em qualquer pasta na máquina do cliente (ex.: `C:\RoboTareffa`).
- **Em um compartilhamento de rede** (`\\servidor\pasta\RoboTareffa`), para servir várias
  instalações a partir de um único lugar.

Em ambos os casos, basta rodar `install.bat` como Administrador na máquina onde o robô deve
rodar (o `.bat` se autoeleva se necessário).

## Sobre o cenário de rede

Um Windows Service, mesmo rodando sob uma conta de domínio, **não enxerga unidades de rede
mapeadas** (letras de unidade como `Z:\`) - isso é uma limitação do Windows, não do WinSW: o
mapeamento de unidade é por sessão de usuário, e o serviço roda fora de qualquer sessão de
logon interativa. Por isso:

- Se `target/dist` estiver em um compartilhamento acessado via **caminho UNC**
  (`\\servidor\pasta\...`), o instalador usa esse caminho diretamente.
- Se estiver em uma **unidade mapeada**, o instalador resolve automaticamente o caminho UNC
  real por trás da letra de unidade e usa esse caminho UNC no `winsw.xml` gerado.
- Em qualquer um dos dois casos, o instalador pede um usuário (`DOMINIO\usuario`) e senha com
  permissão de leitura/escrita naquele compartilhamento, e configura o serviço para rodar sob
  essa conta (bloco `<serviceaccount>` do WinSW) em vez da conta padrão `LocalSystem`.
- Depois de confirmar que o serviço está rodando normalmente, a linha `<password>` pode ser
  removida de `winsw.xml` - o Windows já armazena a credencial de logon do serviço de
  forma criptografada internamente (Local Security Authority), então o texto em claro no XML
  só é necessário no momento da instalação.

## Desinstalação

Rode `uninstall.bat` (também como Administrador) na mesma pasta - ele para e remove o
serviço `RoboTareffa` via WinSW.
