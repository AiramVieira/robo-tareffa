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
      ficam os clientes (ex.: `C:/Tareffa`).
- [ ] O arquivo de credenciais fornecido pela Ottimizza (ou os dados para preenchê-lo
      — veja o Passo 3).

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

1. Dentro da pasta do robô, clique com o botão direito em `parametros.txt` e escolha
   **Abrir com → Bloco de Notas**.
2. Preencha (ou confira) estas três linhas:

   ```
   CONTABILIDADE=NomeDoEscritorio
   PASTA_INICIAL=C:/Tareffa
   NIVEIS_SUBPASTA=2
   ```

   | Campo | O que colocar |
   |---|---|
   | `CONTABILIDADE` | O nome do escritório de contabilidade (sem acentos é mais seguro). |
   | `PASTA_INICIAL` | O caminho da pasta onde ficam as pastas dos clientes, sempre com barra `/` (não `\`). |
   | `NIVEIS_SUBPASTA` | Quantas pastas existem entre a pasta inicial e a pasta de cada cliente. Se não souber, pergunte à Ottimizza — o valor errado faz o robô não encontrar os arquivos. |

3. Salve o arquivo (`Ctrl+S`) e feche o Bloco de Notas.

> Não mexa nas demais linhas do arquivo a menos que a Ottimizza peça especificamente —
> elas são opcionais e já vêm com um exemplo comentado.

---

## Passo 3 — Preencher o arquivo `credenciais.properties`

Este arquivo guarda a senha de acesso do robô ao sistema da Ottimizza.

- **Se a Ottimizza já enviou o arquivo pronto**: apenas confirme que ele está dentro da
  pasta do robô, com o nome exato `credenciais.properties` (sem `.example` no final).
- **Se você precisa preencher você mesmo**: abra `credenciais.properties` no Bloco de
  Notas e complete os 4 campos com os dados que a Ottimizza te passou:

  ```
  AUTH_SERVER_URL=...
  CLIENT_ID=...
  CLIENT_SECRET=...
  SENHA_INTEGRACAO=...
  ```

  Salve e feche.

> Este arquivo contém dados sensíveis — não o envie por e-mail nem o compartilhe fora
> da equipe responsável pela instalação.

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
4. Aguarde. Ao final, a janela deve mostrar:

   ```
   Servico 'RoboTareffa' instalado e iniciado.
   Robo instalado e iniciado com sucesso.
   ```

5. Pressione qualquer tecla para fechar a janela. **A partir daqui, o robô já está
   rodando** — a janela pode ser fechada com segurança.

---

## Passo 5 — Confirmar que está funcionando

Duas formas simples de checar:

**A) Pela tela de Serviços do Windows**
1. Aperte `Windows + R`, digite `services.msc` e tecle Enter.
2. Procure por **"Robo Tareffa - Guias de Contas Pagas"** na lista.
3. Na coluna Status, deve aparecer **Em execução**.

**B) Deixando um arquivo de teste**
1. Coloque um PDF de teste na pasta de um cliente monitorada pelo robô.
2. Aguarde cerca de 1 minuto.
3. O arquivo deve desaparecer da pasta original e reaparecer dentro de uma pasta
   `ENVIADOS` (criada automaticamente ao lado da pasta do cliente).

---

## Se algo der errado

| O que você viu | O que fazer |
|---|---|
| `winsw.exe não encontrado` | A pasta do robô está incompleta. Peça à Ottimizza o pacote completo novamente. |
| A instalação termina com "Falha na instalação" | Feche tudo, confirme os Passos 2 e 3, e rode `install.bat` de novo. |
| O robô não move nenhum arquivo | Reveja o `parametros.txt` (Passo 2) — o valor de `NIVEIS_SUBPASTA` é o erro mais comum. |
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
