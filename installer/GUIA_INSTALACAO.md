# Guia de Instalação — Robô de Guias de Contas Pagas

Este guia explica, passo a passo, como instalar o robô em um computador. Não é
necessário nenhum conhecimento técnico — basta seguir a ordem abaixo.

O robô fica rodando sozinho em segundo plano (como um serviço do Windows). Depois de
instalado, você **não precisa deixar nenhuma janela aberta** nem se lembrar de
executar nada — ele continua funcionando mesmo depois de reiniciar o computador.

---

## Antes de começar

Confira se você tem:

- [ ] A pasta completa do robô, recebida da Ottimizza. Ela deve conter, entre outros,
      os arquivos `robo.jar`, `install.bat`, `winsw.exe` e a pasta `runtime`.
- [ ] Acesso de **Administrador** no computador onde o robô vai rodar.
- [ ] As informações do escritório (contabilidade): nome, e o caminho da pasta onde
      ficam os clientes (ex.: `C:/Tareffa`, ou `\\SERVIDOR\Clientes` se estiver na rede).
- [ ] Um arquivo `mapa-pastas.txt`, **apenas se** a Ottimizza tiver enviado um (veja o
      Passo 2b). Na maioria das instalações ele não é necessário.

> Se algum desses itens estiver faltando, fale com a Ottimizza antes de continuar.

---

## Passo 1 — Colocar a pasta no computador

Decida onde o robô vai rodar:

- **Num computador específico**: copie a pasta inteira para um local fixo nesse
  computador, por exemplo `C:\RoboTareffa`.
- **Numa pasta de rede compartilhada**: copie a pasta inteira para o compartilhamento
  (ex.: `\\SERVIDOR\Compartilhado\RoboTareffa`) e rode a instalação a partir de lá, no
  computador que vai efetivamente executar o robô.

Em qualquer um dos dois casos, **não separe os arquivos** — mantenha tudo dentro da
mesma pasta, do jeito que foi recebido.

---

## Passo 2 — Preencher o arquivo `parametros.txt`

1. Dentro da pasta do robô você vai encontrar `parametros.txt.example`. Faça uma **cópia**
   dele e renomeie a cópia para `parametros.txt` (ou seja: tire o `.example` do final).
   Se `parametros.txt` já existir, use o que já está lá.

   > O Windows pode esconder o final do nome. Se não aparecer o `.example`, abra a aba
   > **Exibir** do Explorador de Arquivos e marque **Extensões de nomes de arquivos**.

2. Clique com o botão direito em `parametros.txt` e escolha **Abrir com → Bloco de Notas**.
   Preencha (ou confira) estas três linhas:

   ```
   CONTABILIDADE=NomeDoEscritorio
   PASTA_INICIAL=C:/Tareffa
   NIVEIS_SUBPASTA=2
   ```

   | Campo | O que colocar |
   |---|---|
   | `CONTABILIDADE` | O nome do escritório de contabilidade (sem acentos é mais seguro). |
   | `PASTA_INICIAL` | O caminho da pasta onde ficam as pastas dos clientes. Pode usar `/` ou `\`. Se a pasta estiver num servidor, veja o aviso abaixo. |
   | `NIVEIS_SUBPASTA` | Quantas pastas existem entre a pasta inicial e a pasta de cada cliente. Se não souber, pergunte à Ottimizza — o valor errado faz o robô não encontrar os arquivos. Depois de preencher, confira com o Passo 5. |

3. Salve o arquivo (`Ctrl+S`) e feche o Bloco de Notas.

> ⚠️ **Se as pastas dos clientes ficam num servidor da rede**, não use a letra da unidade
> mapeada (como `W:/Clientes`). O robô roda como serviço do Windows, e um serviço **não
> enxerga unidades mapeadas** — o resultado é o robô funcionando sem nunca enviar nada.
> Use o caminho completo do servidor:
>
> ```
> PASTA_INICIAL=\\SERVIDOR\Clientes
> ```
>
> O `testar.bat` do Passo 5 descobre esse caminho para você e mostra na tela o que colar aqui.

> Não mexa nas demais linhas do arquivo a menos que a Ottimizza peça especificamente —
> elas são opcionais e já vêm com um exemplo comentado.

---

## Passo 2b — Mapa de pastas (só quando a Ottimizza pedir)

Em alguns escritórios a estrutura de pastas é irregular — departamentos diferentes com
quantidades diferentes de subpastas, pastas de mês e ano no meio do caminho. Nesses casos a
Ottimizza envia um arquivo chamado **`mapa-pastas.txt`**, que descreve os caminhos a monitorar.

O que fazer:

1. Coloque o `mapa-pastas.txt` **na mesma pasta do `robo.jar`**, junto com o `parametros.txt`.
2. Se a Ottimizza pedir para colar linhas novas nele, cole **exatamente** como foram enviadas,
   uma por linha, sem reordenar nem "arrumar" espaços.
3. Rode o `testar.bat` (Passo 5) e confira a lista de pastas que aparece.

> A simples presença desse arquivo muda o comportamento do robô: as linhas
> `NIVEIS_SUBPASTA`, `CUSTOMIZACAO` e `SUBNIVEL_ANO` do `parametros.txt` passam a ser
> ignoradas. Para voltar atrás, renomeie o arquivo para `mapa-pastas.txt.off`.
>
> Se você não recebeu esse arquivo, **não crie um** — o robô funciona normalmente sem ele.

---

## Passo 3 — Credenciais: nada a fazer

Nas versões anteriores era preciso preencher um arquivo `credenciais.properties` nesta
etapa. **Ele não existe mais.** As credenciais de acesso ao sistema da Ottimizza já vêm
dentro do próprio programa, cifradas — não há nada para preencher, e nenhum dado sensível
fica visível na pasta do robô.

Se você está atualizando uma instalação antiga e existe um `credenciais.properties` na
pasta, ele passou a ser ignorado e pode ser apagado.

O `testar.bat` (Passo 5) confirma numa linha se as credenciais foram lidas corretamente.

---

## Passo 4 — Executar a instalação

1. Dentro da pasta do robô, dê **duplo clique em `install.bat`**.
2. O Windows vai perguntar: *"Deseja permitir que este aplicativo faça alterações no
   dispositivo?"* — clique em **Sim**.
3. Se a pasta do robô estiver em uma **rede compartilhada**, o instalador vai pedir um
   usuário e senha do Windows com permissão de acesso a essa pasta:
   - **Usuário**: digite no formato `DOMINIO\usuario` (ou peça esse dado a quem cuida
     da rede, caso não saiba).
   - **Senha**: digite e pressione Enter (os caracteres não aparecem na tela — isso é
     normal, é só para proteger a senha).
4. Aguarde. O instalador ainda confere, por cerca de 20 segundos, se o serviço **continua**
   no ar depois de iniciar. Ao final, a janela deve mostrar:

   ```
   Servico 'RoboTareffa' instalado e RODANDO (verificado).
   Robo instalado e iniciado com sucesso.
   ```

   Se aparecer `FALHA:` em vermelho, o instalador mostra as últimas linhas do log com o
   motivo e **remove o serviço automaticamente** — nada fica pela metade. Nesse caso vá
   para a seção "Se algo der errado".

5. Pressione qualquer tecla para fechar a janela. **A partir daqui, o robô já está
   rodando** — a janela pode ser fechada com segurança.

---

## Passo 5 — Confirmar que está funcionando

**A) Rodando o `testar.bat`** — comece por aqui

É a verificação mais rápida e a que mais explica. Dê **duplo clique em `testar.bat`**
(não pede senha de administrador — ele só lê, não altera nada).

A janela vai mostrar a lista de **pastas que o robô vai monitorar**. Confira se ela faz
sentido:

- **Aparece a lista das pastas certas?** Está tudo pronto.
- **A lista está vazia** (`nenhuma pasta encontrada`)? O problema está no `parametros.txt`
  ou no mapa de pastas, **não** no envio. A própria tela indica o que revisar.
- **Aparece `CONFIGURACAO INVALIDA`?** A mensagem diz exatamente qual linha e qual campo
  corrigir. Volte ao Passo 2.
- **Aparece uma seção `RAMOS SEM PASTA-ALVO`?** São pastas que o robô entrou mas onde não
  achou o que esperava. Envie o relatório à Ottimizza.

O relatório completo também fica salvo em `logs\teste-pastas-<data>.txt` — é esse arquivo
que você envia à Ottimizza se algo não estiver certo.

> Você pode rodar o `testar.bat` **antes** de instalar o serviço, e quantas vezes quiser
> depois. Ele nunca move, renomeia nem envia arquivo nenhum.

**B) Pela tela de Serviços do Windows**
1. Aperte `Windows + R`, digite `services.msc` e tecle Enter.
2. Procure por **"Robo Tareffa - Guias de Contas Pagas"** na lista.
3. Na coluna Status, deve aparecer **Em execução**.

**C) Deixando um arquivo de teste**
1. Coloque um PDF de teste na pasta de um cliente monitorada pelo robô.
2. Aguarde cerca de 1 minuto.
3. O arquivo deve desaparecer da pasta original e reaparecer dentro de uma pasta
   `ENVIADOS` (criada automaticamente ao lado da pasta do cliente).

---

## Se algo der errado

> **Onde está o log:** todo erro do serviço fica em `logs\winsw.wrapper.log`, dentro da pasta
> do robô. É o primeiro arquivo a abrir (Bloco de Notas) quando algo não funciona, e o
> primeiro a enviar à Ottimizza.

| O que você viu | O que fazer |
|---|---|
| `não é um pacote de instalação completo` (lista `FALTANDO:`) | Você está rodando o `install.bat` da pasta errada. Use a pasta do robô que a Ottimizza enviou (a que tem `robo.jar` e a pasta `runtime`), não a pasta do código-fonte. |
| `winsw.exe não encontrado` | A pasta do robô está incompleta. Peça à Ottimizza o pacote completo novamente. |
| `FALHA: o servico iniciou mas nao se manteve no ar` | O instalador já removeu o serviço. Abra `logs\winsw.wrapper.log`, veja a última linha `ERROR` e envie à Ottimizza. |
| A instalação termina com "Falha na instalação" | Feche tudo, confirme o Passo 2, e rode `install.bat` de novo. |
| O robô não move nenhum arquivo | Rode o `testar.bat` **primeiro** (Passo 5-A). Se ele listar **0 pastas**, o problema é a `PASTA_INICIAL`/`NIVEIS_SUBPASTA`/mapa de pastas — não o envio. Envie `logs\teste-pastas-<data>.txt` à Ottimizza. |
| O `testar.bat` mostra `nenhuma pasta encontrada` | O valor de `NIVEIS_SUBPASTA` é o erro mais comum. Se a pasta dos clientes está num servidor, confira também o aviso sobre unidade mapeada no Passo 2. |
| `PASTA_INICIAL=... nao foi encontrada` / aviso de unidade mapeada | A pasta está numa letra de unidade que o serviço não enxerga. Troque pelo caminho `\\SERVIDOR\Pasta` que o `testar.bat` sugere (Passo 2). |
| O `testar.bat` mostra `Credenciais: FALHA` | Não é nada que se corrija aqui: o pacote foi montado com problema. Envie essa linha à Ottimizza e peça um pacote novo. As pastas continuam sendo conferidas normalmente no mesmo relatório. |
| `mapa-pastas.txt linha N: ...` | Abra o `mapa-pastas.txt` no Bloco de Notas, vá até a linha **N** e compare com o que a Ottimizza enviou. Se não achar a diferença, envie o arquivo à Ottimizza. |
| Um arquivo foi movido mas parece ter falhado | Isso é registrado automaticamente dentro de `logs\erros` (pasta do robô) — envie o
conteúdo desse arquivo à Ottimizza para diagnóstico. |

Se nada disso resolver, envie à Ottimizza:
- O conteúdo da pasta `logs` (dentro da pasta do robô);
- Uma descrição do que foi tentado e o que apareceu na tela.

---

## Como desinstalar

Caso precise remover o robô de um computador:

1. Dentro da pasta do robô, dê duplo clique em **`uninstall.bat`**.
2. Confirme a janela de administrador, como no Passo 4.
3. Aguarde a mensagem **"Servico removido."**

A pasta do robô (e os arquivos já enviados) não são apagados — apenas o serviço em
segundo plano é removido.
