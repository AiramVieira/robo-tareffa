# Documentação Funcional — Robô de Envio de Guias de Contas Pagas

> Objetivo deste documento: servir de especificação funcional completa para a construção deste robô do zero, em uma nova linguagem/plataforma, cobrindo somente as regras de negócio — sem vínculo com nenhuma implementação, arquivo ou plataforma anterior.

---

## 1. Visão Geral e Objetivo de Negócio

O robô monitora continuamente, dentro da estrutura de pastas de um escritório de contabilidade, os documentos de **guias de contas pagas** (comprovantes/guias de pagamento) depositados por clientes, e os envia automaticamente para o storage em nuvem do Tareffa, associados ao tenant (escritório de contabilidade) correspondente — eliminando o upload manual desses documentos.

O robô roda como um **serviço único e contínuo**: a cada ciclo de varredura, ele percorre a estrutura de pastas configurada e, assim que encontra um arquivo pendente, reserva-o e o envia imediatamente — sem lotes, sem etapas separadas e sem fila intermediária.

---

## 2. Arquitetura (Serviço Único e Contínuo)

O robô é implementado como um serviço que fica ativo o tempo todo. Ele opera em ciclos: a cada intervalo configurável (seção 3), o robô:

1. Recarrega a configuração (`parametros.txt`).
2. Obtém a lista de pastas-alvo (usando o cache descrito na seção 6.2, quando disponível).
3. Para cada pasta-alvo, procura arquivos pendentes de envio.
4. Para cada arquivo encontrado, executa — na mesma passada — a reserva do arquivo, a autenticação e o envio (seção 7), um arquivo de cada vez.
5. Aguarda o intervalo configurado e repete o ciclo.

Não existe separação entre uma etapa de "descoberta" e uma etapa de "envio" rodando de forma independente, nem lote/fila de arquivos aguardando para serem enviados em conjunto: **cada arquivo é enviado individualmente, assim que é encontrado**.

Todo o estado de progresso do robô é mantido **em disco**, não em banco de dados:

- Um arquivo de cache com a lista de pastas já mapeadas, na raiz configurada (seção 6.2).
- O próprio nome e a própria localização de cada arquivo do cliente (pasta de origem → pasta "ENVIADOS", com um marcador textual usado apenas durante a curta janela em que o envio está em andamento) — ver seção 8.

---

## 3. Configuração — `parametros.txt`

| Campo | Obrigatório | Formato / Valores | Efeito | Exemplo |
|---|---|---|---|---|
| `CONTABILIDADE` | Sim | Texto livre | Identifica o tenant (escritório de contabilidade). É convertido para "Title Case" (primeira letra de cada palavra maiúscula) e usado em logs, no e-mail de integração e na URL de upload. O ciclo é ignorado se vazio. | `Nomeresumido` → exibido como `Nomeresumido` |
| `PASTA_INICIAL` | Sim | Caminho absoluto contendo `:/ ` (ex.: `C:/Tareffa`) | Pasta raiz a partir da qual a árvore de clientes é percorrida. O ciclo é ignorado se vazio ou sem `":/"`. | `C:/Tareffa` |
| `NIVEIS_SUBPASTA` | Sim | Número inteiro | Profundidade (a partir da raiz, que conta como nível 0) em que ficam as pastas "alvo" de cada cliente. `0` = os arquivos ficam direto na raiz (modo pasta única, sem percorrer subpastas). O ciclo é ignorado se não for um inteiro válido. | `2` |
| `INTERVALO_VARREDURA_SEGUNDOS` | Não (padrão sugerido: `30`) | Número inteiro (segundos) | Tempo de espera entre o fim de um ciclo de varredura de arquivos e o início do próximo. Não afeta a velocidade de remapeamento das pastas (ver `INTERVALO_REMAPEAMENTO_HORAS`). | `30` |
| `INTERVALO_REMAPEAMENTO_HORAS` | Não (padrão sugerido: `24`) | Número inteiro (horas) | De quanto em quanto tempo a lista de pastas-alvo é refeita do zero (varredura completa da árvore), para capturar pastas de clientes novas/removidas. Independente de `INTERVALO_VARREDURA_SEGUNDOS` — ver seção 6.2. | `24` |
| `PASTA_ENVIAR` | Não | Texto (nome de subpasta) | Nome de uma subpasta, dentro de cada pasta-alvo, onde os arquivos a enviar realmente ficam. Se vazio, os arquivos são lidos direto da pasta-alvo. A subpasta é criada automaticamente se não existir. | `ENVIAR` |
| `ENVIADO_DATADO` | Não | `SIM` (não sensível a maiúsculas) ou vazio/outro | Se `SIM`, a pasta de backup dos arquivos processados é organizada em `ENVIADOS/{ano}/{mês}` (data do **processamento**, não do documento). Caso contrário, usa uma única pasta `ENVIADOS/` sem subdivisão. | `SIM` |
| `CUSTOMIZACAO` | Não | Lista de regras separadas por `;`, cada uma no formato `nivel:operadorTermo` | Regras de exceção para a varredura de pastas quando a estrutura de um cliente foge do padrão. Ver seção 6.3. | `1:>!INATIVO;2:+FILIAL` |
| `SUBNIVEL_ANO` | Não* | Número inteiro, `0`, ou texto contendo "ULTIM"/"ÚLTIM" | Nível em que existem pastas de ano no cliente, para filtrar quais anos o robô deve acessar. `0` ou "último/última" amarra o filtro ao nível-alvo final (após ajustes de `CUSTOMIZACAO`). Ver seção 6.4. | `2` |
| `VARIACAO_ANOS` | Não* | Texto contendo `+`, `-` e/ou um número | Define quantos anos para trás e/ou para frente do ano atual são aceitos quando `SUBNIVEL_ANO` está configurado. | `+-1`, `-2`, `+1` |

\* `SUBNIVEL_ANO` e `VARIACAO_ANOS` são opcionais como par, mas se `SUBNIVEL_ANO` for preenchido, `VARIACAO_ANOS` passa a ser **obrigatório** — a combinação incompleta é tratada como configuração inválida (ver seção 6.5).

Constante que hoje **não** é configurável via `parametros.txt` e está fixa no comportamento esperado do robô:

- Extensões aceitas: **`.pdf`, `.txt`, `.csv`, `.rar`, `.zip`** (ver seção 7.1 sobre por que variantes em maiúsculo dessa lista seriam redundantes).

---

## 4. Registro de Erros (Logs)

Falhas durante o envio de um arquivo (seção 7.7) não interrompem o robô — à parte a renovação de token em caso de 401 (seção 7.4), não há nova tentativa automática. São consideradas raras/aceitáveis e apenas registradas para consulta posterior.

- **Local**: dentro da própria pasta onde o robô está instalado, em uma subpasta `logs`. Exemplo: se o robô estiver instalado em `C:/RoboTareffa`, os erros são registrados em algo como `C:/RoboTareffa/logs/erros`.
- **Gatilho**: qualquer falha ao enviar um arquivo (seção 7.7).

Esta documentação não define o formato interno do log (uma linha por evento, formato do arquivo, rotação por data etc.) — apenas o local e o gatilho.

---

## 5. Fluxo Resumido (ponta a ponta)

1. O robô inicia como serviço e entra em um ciclo contínuo.
2. A cada ciclo, recarrega `parametros.txt` e valida `CONTABILIDADE`, `PASTA_INICIAL` e `NIVEIS_SUBPASTA`; se algo estiver inválido, o ciclo é ignorado (nenhum arquivo é tocado) e o robô tenta de novo no próximo ciclo — sem precisar reiniciar o serviço.
3. Obtém a lista de pastas-alvo (via cache em disco ou varredura recursiva com as regras de `CUSTOMIZACAO`/`SUBNIVEL_ANO`).
4. Para cada pasta-alvo, normaliza extensões e localiza arquivos com extensão aceita.
5. Para cada arquivo encontrado: move-o para a pasta de backup ("ENVIADOS"), marcando o nome com `" ARQUIVO ROBO"`, e envia o arquivo imediatamente para o storage do Tareffa (reautenticando antes, apenas se o token atual tiver sido rejeitado com 401).
6. Após a tentativa de envio — sucesso ou falha —, remove o marcador do nome do arquivo, restaurando o nome original dentro de "ENVIADOS". Se o envio falhou, registra o erro no log (seção 7.7) antes de seguir para o próximo arquivo.
7. Falhas ao ler/preparar um arquivo (antes da reserva) colocam-no em uma pasta "ERROS"; configuração inválida faz o ciclo inteiro ser ignorado, sem tocar em nenhum arquivo.
8. Aguarda `INTERVALO_VARREDURA_SEGUNDOS` e recomeça do passo 2 (o remapeamento completo das pastas, seção 6.2, roda em um intervalo próprio e muito mais espaçado, sem atrasar este ciclo).

---

## 6. Descoberta das Pastas-Alvo

### 6.1 Inicialização e validações

A cada ciclo, o robô:

1. Recarrega `parametros.txt`.
2. Lê `CONTABILIDADE`, `PASTA_INICIAL`, `PASTA_ENVIAR`, `ENVIADO_DATADO`, `CUSTOMIZACAO`.
3. Tenta converter `NIVEIS_SUBPASTA` para inteiro.
4. Registra em log o nome da contabilidade e cada regra de customização configurada.

O ciclo é encerrado **imediatamente, sem processar nenhum arquivo**, se:

- `NIVEIS_SUBPASTA` não for um número válido;
- `CONTABILIDADE` estiver vazia;
- `PASTA_INICIAL` estiver vazia ou não contiver `":/"`.

Como a configuração é recarregada a cada ciclo, uma correção no `parametros.txt` passa a valer no próximo ciclo, sem precisar reiniciar o serviço.

### 6.2 Determinação das pastas-alvo (cache de estrutura)

É importante separar dois conceitos que rodam em velocidades diferentes:

- **Varredura de arquivos** (a cada `INTERVALO_VARREDURA_SEGUNDOS`, ex.: 30 segundos): dentro de cada pasta-alvo **já conhecida**, procura arquivos novos e os envia. Isso roda sempre no intervalo curto — é o que garante que um arquivo seja enviado poucos segundos depois de ser colocado na pasta monitorada.
- **Remapeamento da árvore** (a cada `INTERVALO_REMAPEAMENTO_HORAS`, ex.: 24 horas): refaz a varredura completa da estrutura de pastas a partir da raiz, para descobrir **quais pastas existem** — isto é, detectar clientes novos, clientes removidos, ou mudanças que alterem quais pastas se encaixam nas regras de `CUSTOMIZACAO`/`SUBNIVEL_ANO`. Isso não precisa ser rápido: enquanto a lista de pastas-alvo não muda estruturalmente, o robô continua enviando arquivos das pastas já conhecidas normalmente, no ritmo da varredura de arquivos.

Ou seja: **o remapeamento controla apenas a integridade da lista de pastas monitoradas, nunca a velocidade de envio dos arquivos dentro delas.** Por isso o intervalo de remapeamento pode ser bem mais espaçado (ex.: uma vez por dia) sem qualquer impacto no tempo entre um arquivo ser adicionado pelo cliente e ser enviado.

Funcionamento:

- O resultado do remapeamento é **cacheado em disco**. Enquanto o cache estiver dentro do prazo de `INTERVALO_REMAPEAMENTO_HORAS`, a lista de pastas-alvo é lida diretamente dele em cada ciclo de varredura de arquivos, sem refazer a varredura da árvore (e sem reavaliar as regras de `CUSTOMIZACAO`/`SUBNIVEL_ANO` — ver observação na seção 6.5).
- Se `NIVEIS_SUBPASTA = 0`, a lista é sempre a própria `PASTA_INICIAL` (modo pasta única, sem necessidade de cache/remapeamento).
- Se `NIVEIS_SUBPASTA > 0` e o cache estiver ausente ou vencido (primeira vez, ou passado o `INTERVALO_REMAPEAMENTO_HORAS` desde o último remapeamento), é feita uma varredura recursiva completa (seções 6.3/6.4) e o resultado é salvo em cache novamente.

### 6.3 Motor de regras de `CUSTOMIZACAO`

Usado quando a estrutura de pastas de um cliente foge do padrão configurado em `NIVEIS_SUBPASTA`. `NIVEIS_SUBPASTA` deve sempre representar o **menor** nível válido entre os clientes; exceções (clientes com um nível a mais) são tratadas com regras de customização.

Formato de cada regra: `nivel:operadorTermo`, várias regras separadas por `;`. O `nivel` é comparado à profundidade atual da pasta sendo avaliada durante a varredura (raiz = nível 0). O `termo` é comparado (contém, não sensível a maiúsculas, ignorando caracteres especiais) apenas contra o **nome da própria pasta** naquele nível — não o caminho completo.

Dois operadores, cada um podendo ser negado com `!` logo após o operador:

| Regra | Efeito |
|---|---|
| `nivel:+termo` | Se o nome da pasta **contém** `termo` nesse nível, a profundidade-alvo é aumentada em 1 **para esse ramo da árvore** (a pasta-alvo passa a ser um nível mais profundo ali). |
| `nivel:+!termo` | Mesma coisa, mas quando o nome da pasta **não contém** `termo`. |
| `nivel:>termo` | Só continua a varredura por pastas, nesse nível, cujo nome **contém** `termo` (funciona como uma lista de permissão — as demais são ignoradas). |
| `nivel:>!termo` | Só continua a varredura por pastas, nesse nível, cujo nome **não contém** `termo` (funciona como uma lista de bloqueio). |

Exemplo ilustrativo (estrutura padrão de 2 níveis, com uma exceção e um cliente inativo a ignorar):

```
C:/Tareffa                          nível 0 (raiz)
├── EMPRESA ATIVA LTDA              nível 1
│   └── 2025                        nível 2  (pasta-alvo padrão, NIVEIS_SUBPASTA=2)
├── EMPRESA INATIVA LTDA            nível 1  (deve ser ignorada)
│   └── 2025
└── EMPRESA CONSOLIDADO LTDA        nível 1  (tem um nível extra: FILIAL)
    └── FILIAL SP
        └── 2025                    nível 3  (pasta-alvo real deste cliente)
```

Configuração que trata os dois casos de exceção acima:
`CUSTOMIZACAO = 1:>!INATIVO;1:+CONSOLIDADO`

- `1:>!INATIVO` — no nível 1, só entra em pastas cujo nome **não** contenha "INATIVO".
- `1:+CONSOLIDADO` — no nível 1, se o nome contiver "CONSOLIDADO", soma 1 ao nível-alvo apenas para esse ramo (passa de 2 para 3), fazendo o robô descer até a pasta `FILIAL SP/2025`.

Qualquer regra malformada (nível não numérico, ou termo que não comece com `+` ou `>`) marca toda a estrutura como inválida (ver seção 6.5).

### 6.4 Filtro de ano (`SUBNIVEL_ANO` / `VARIACAO_ANOS`)

Usado apenas quando o cliente **não** tem uma pasta dedicada de "enviar" e sua estrutura contém uma pasta por ano — sem esse filtro, o robô varreria e enviaria arquivos de anos antigos já processados manualmente.

- `SUBNIVEL_ANO` indica em qual nível da árvore está a pasta do ano. Se configurado como `0` ou contendo "ÚLTIMO/ÚLTIMA" (não sensível a maiúsculas/acentuação simples), o filtro passa a valer no **nível-alvo final** (útil combinado com regras de `CUSTOMIZACAO` que alteram a profundidade dinamicamente).
- `VARIACAO_ANOS` define a tolerância em torno do ano corrente:
  - Contém `+` e `-`: aceita `N` anos para trás **e** para frente do ano atual.
  - Contém só `+`: aceita apenas até `N` anos **à frente**.
  - Contém só `-`: aceita apenas até `N` anos **atrás**.
  - Sem `+`/`-` ou sem dígitos: aceita somente o ano corrente.
  - `N` é extraído dos dígitos presentes em `VARIACAO_ANOS`.
- A checagem do ano é feita contra o **caminho completo** acumulado até aquele ponto (não apenas o nome da pasta do nível em questão) — isso permite estruturas "ano depois mês", mas também significa que um ano "aceito" que apareça em qualquer pasta ancestral do caminho já satisfaz a regra.

Exemplo: `SUBNIVEL_ANO = 2`, `VARIACAO_ANOS = -1`, ano atual 2026 → nas pastas de ano do nível 2, apenas `2025` e `2026` são percorridas; `2024` e anteriores são ignoradas.

### 6.5 Configuração inválida

Se qualquer regra de `CUSTOMIZACAO` for malformada, ou se `SUBNIVEL_ANO` estiver preenchido sem `VARIACAO_ANOS`, o remapeamento marca a estrutura como inválida. Nesse caso:

- Nenhum arquivo é tocado neste ciclo.
- O cache recém-gerado (que contém o problema) é descartado.
- O robô tenta remapear novamente a cada ciclo seguinte, até que a configuração seja corrigida.

Atenção operacional: como a lista de pastas só é revalidada quando o cache é reconstruído (seção 6.2), uma alteração em `CUSTOMIZACAO`/`SUBNIVEL_ANO` só é efetivamente validada/aplicada no próximo remapeamento (a cada `INTERVALO_REMAPEAMENTO_HORAS`) — não no próximo ciclo de varredura de arquivos, se já existir um cache válido de uma configuração anterior.

---

## 7. Processamento de Cada Arquivo Encontrado

### 7.1 Preparação da pasta e normalização de extensão

Para cada pasta-alvo:

1. Monta o caminho da pasta de onde ler arquivos: `pastaAlvo/PASTA_ENVIAR` (ou apenas `pastaAlvo`, se `PASTA_ENVIAR` estiver vazio). Se a pasta não existir, ela é criada — inclusive recriando uma pasta que porventura tenha sido apagada manualmente entre ciclos.
2. Monta o caminho da pasta de backup ("ENVIADOS"), com ou sem subdivisão por ano/mês conforme `ENVIADO_DATADO` (data do ciclo em que o arquivo foi encontrado).
3. **Normaliza a extensão de TODOS os arquivos** presentes na pasta de leitura para minúsculo (ex.: `NOTA.PDF` → `NOTA.pdf`), independentemente de o arquivo ser um candidato de envio ou não. Arquivos vazios (0 bytes) não são renomeados nesta etapa. Esse passo roda antes de qualquer filtro de extensão — na prática, isso torna variantes em maiúsculo da lista de extensões aceitas (`.PDF`, `.TXT`, `.CSV`, `.RAR`, `.ZIP`) inalcançáveis, já que nenhum arquivo chega a elas ainda em maiúsculo.
4. Para cada extensão aceita (`.pdf`, `.txt`, `.csv`, `.rar`, `.zip`), localiza os arquivos correspondentes na pasta de leitura.

### 7.2 Reserva do arquivo (marcador `" ARQUIVO ROBO"`)

Para cada arquivo encontrado:

1. Obtém o nome do arquivo.
2. Move o arquivo para a pasta de backup "ENVIADOS", renomeando-o para inserir o marcador `" ARQUIVO ROBO"` imediatamente antes da extensão. Exemplo: `guia_pagamento.pdf` → `guia_pagamento ARQUIVO ROBO.pdf`. Esse é o estado "reservado, envio pendente".
3. Registra em log o nome do arquivo.
4. Segue imediatamente para a autenticação e o envio (7.3/7.4) — cada arquivo é processado sozinho, assim que reservado, sem esperar por outros arquivos.

### 7.3 Autenticação

O robô se autentica **uma única vez** (por exemplo, ao iniciar o serviço, ou antes do primeiro envio) contra a API de autenticação da Ottimizza, usando:

- Usuário: e-mail derivado automaticamente no padrão `integracao@{contabilidade-em-minusculo}.com.br` (não é uma credencial cadastrada manualmente por cliente, é sempre calculada a partir de `CONTABILIDADE`).
- Senha: um valor fixo, literal, igual para todos os tenants (ver ponto de atenção na seção 9).
- Fluxo: OAuth2 "password grant" contra o servidor de autenticação da Ottimizza, autenticado com um client-id/client-secret fixo (Basic Auth).

O token de acesso obtido é usado **sem prefixo** (não como `Bearer <token>`, apenas o valor puro) no cabeçalho `Authorization`, e é **reaproveitado em todos os envios seguintes** — o robô não se autentica de novo a cada arquivo. A renovação desse token só acontece na condição descrita na seção 7.4.

### 7.4 Envio (upload)

1. Monta a URL de destino: `https://s3.tareffaapp.com.br:55325/storage/tareffa-gestao-servicos/accounting/{contabilidade}/store` (um endpoint por tenant).
2. Verifica se o arquivo ainda existe fisicamente no caminho esperado; se não existir, trata-se como falha de envio (seção 7.7).
3. Envia o arquivo via HTTP POST `multipart/form-data`, em um único campo chamado `file`, contendo apenas os bytes do arquivo (nenhum outro metadado de negócio é enviado junto, como nome original, data, tipo de documento etc.), usando o token atual (seção 7.3) no cabeçalho `Authorization`.
4. **Se a resposta for 401 (não autorizado)**: o robô se autentica novamente (uma única vez) e repete o envio desse mesmo arquivo com o novo token — esse novo token passa a ser o token reaproveitado dali em diante. Se essa nova tentativa falhar de novo (outro 401, ou qualquer outra falha), o envio é tratado como falha (seção 7.7) e não há mais nenhuma nova tentativa de autenticação.
5. Qualquer outra resposta que não indique sucesso é tratada diretamente como falha de envio (seção 7.7), sem tentativa de renovação de token.

### 7.5 Finalização

**Após a tentativa de envio — com sucesso ou com falha — o marcador `" ARQUIVO ROBO"` é removido do nome do arquivo**, restaurando o nome original, sempre dentro da pasta "ENVIADOS". A única diferença entre um envio bem-sucedido e um envio que falhou é que a falha fica registrada no log de erros (seção 7.7); o arquivo, seu nome e sua localização final são os mesmos nos dois casos.

### 7.6 Falha ao ler/preparar o arquivo (quarentena)

Se o carregamento de um arquivo (antes mesmo de ele ser reservado, na etapa 7.1) falhar (ex.: arquivo corrompido ou ilegível):

1. O erro é registrado em log.
2. É feita uma tentativa de identificar qual arquivo especificamente causou o problema.
3. Uma pasta "ERROS" (irmã da pasta "ENVIADOS") é criada, se necessário.
4. O arquivo identificado é movido para essa pasta "ERROS", tirando-o do caminho do robô para não bloquear os próximos ciclos.
5. Se essa identificação/movimentação também falhar, apenas um novo erro é registrado em log e o arquivo problemático permanece onde estava (ficará sujeito a nova tentativa de leitura no próximo ciclo).

Se o processamento de uma pasta-alvo inteira falhar de forma inesperada, o erro é logado e o robô segue normalmente para a próxima pasta-alvo — uma falha isolada não interrompe o ciclo inteiro.

### 7.7 Falha no envio

Se o envio de um arquivo já reservado (7.2) falhar — erro de conexão, exceção durante a transferência, resposta do servidor que não indique sucesso (incluindo um 401 que persista mesmo após a renovação de token da seção 7.4), ou o arquivo não ser encontrado no momento do envio (7.4, passo 2) — o robô registra o ocorrido no log de erros (seção 4) e segue para o próximo arquivo.

O marcador `" ARQUIVO ROBO"` é removido mesmo nesse caso (seção 7.5): o arquivo final fica com o nome original, dentro de "ENVIADOS", igual a um envio bem-sucedido — a única diferença é o registro no log. Além da renovação de token já descrita na seção 7.4, não há nenhuma outra nova tentativa automática: essas falhas são consideradas raras e aceitáveis, e ficam disponíveis apenas para consulta manual no log, caso necessário.

---

## 8. Máquina de Estados do Arquivo em Disco

O robô não usa banco de dados para saber o que já foi enviado — o estado é o próprio nome/local do arquivo:

```
[Pasta do cliente / pasta "ENVIAR"]
        |
        |  (arquivo encontrado, extensão aceita)
        v
[ENVIADOS/(ano/mês)/nome ARQUIVO ROBO.ext]   <- "reservado, envio em andamento"
        |
        |  (upload tentado imediatamente — sucesso ou falha)
        v
[ENVIADOS/(ano/mês)/nome.ext]   <- estado final, em sucesso OU falha
                                    (uma falha só é distinguível pelo log de erros, seção 7.7)

Ramo de erro de leitura/preparo (antes da reserva):
[Pasta do cliente / pasta "ENVIAR"] --> [ERROS/nome.ext]
```

Consequência direta desse desenho: o marcador `" ARQUIVO ROBO"` existe apenas durante a janela curta entre a reserva do arquivo e a tentativa de envio — como o envio é imediato, essa janela é breve. Depois de tentado, o arquivo sempre termina em "ENVIADOS" com o nome original, tenha o envio dado certo ou não. A pasta "ENVIADOS" deixa de indicar, por si só, se um arquivo específico foi de fato aceito pelo servidor — isso só é possível verificando o log de erros (seção 4).

---

## 9. Pontos de Atenção e Melhorias Recomendadas

1. **Autenticação com credencial fixa e igual para todos os tenants** (seção 7.3) — a senha usada para todo tenant é um valor literal fixo, não um segredo individual gerido com segurança. Recomenda-se, eventualmente, migrar para credenciais/segredos geridos por tenant.
2. **Sem identificador estável por arquivo** além do próprio nome — dificulta correlacionar, no log, diferentes tentativas de envio do mesmo arquivo. Vale considerar um identificador estável (ex.: hash do conteúdo) só para fins de log/diagnóstico.
3. A proteção contra arquivos "Thumbs.db" (arquivo de miniaturas do Windows) é sensível a maiúsculas/minúsculas e só cobre alguns formatos de grafia do nome — vale revisar com um casamento não sensível a caixa se esse filtro ainda for necessário.

---

## 10. Checklist de Regras de Negócio (para conferência da reescrita)

**Configuração e validação**
- [ ] `CONTABILIDADE`, `PASTA_INICIAL` e `NIVEIS_SUBPASTA` são obrigatórios; ausência/valor inválido de qualquer um deles faz o ciclo ser ignorado, sem processar nada.
- [ ] `PASTA_ENVIAR`, `ENVIADO_DATADO`, `CUSTOMIZACAO`, `SUBNIVEL_ANO`, `VARIACAO_ANOS`, `INTERVALO_VARREDURA_SEGUNDOS` e `INTERVALO_REMAPEAMENTO_HORAS` são opcionais, com os efeitos descritos na seção 3.
- [ ] `SUBNIVEL_ANO` sem `VARIACAO_ANOS` é configuração inválida.
- [ ] A configuração é recarregada a cada ciclo (não apenas na inicialização do serviço).

**Descoberta de pastas**
- [ ] `NIVEIS_SUBPASTA = 0` = modo pasta única (sem varredura, sem regras de customização/ano aplicáveis).
- [ ] A varredura de arquivos (a cada `INTERVALO_VARREDURA_SEGUNDOS`) e o remapeamento da árvore de pastas (a cada `INTERVALO_REMAPEAMENTO_HORAS`) são independentes — o remapeamento nunca atrasa o envio de um arquivo em uma pasta já conhecida.
- [ ] A lista de pastas-alvo é cacheada em disco entre remapeamentos e só recalculada quando o cache está ausente ou vencido.
- [ ] Regras `+`/`+!`/`>`/`>!` de `CUSTOMIZACAO` funcionam conforme a tabela da seção 6.3.
- [ ] Filtro de ano (`SUBNIVEL_ANO`/`VARIACAO_ANOS`) funciona conforme a seção 6.4.
- [ ] Configuração de regra inválida faz o remapeamento ser descartado e tentado de novo no próximo ciclo/remapeamento.

**Seleção, reserva e envio de arquivos**
- [ ] Pasta de leitura (`PASTA_ENVIAR` ou a própria pasta-alvo) é criada automaticamente se não existir.
- [ ] Apenas arquivos com extensão `.pdf`, `.txt`, `.csv`, `.rar` ou `.zip` (não sensível a caixa) são considerados.
- [ ] Cada arquivo aceito é movido para a pasta "ENVIADOS" (com ou sem subpasta de ano/mês conforme `ENVIADO_DATADO`) e recebe o marcador `" ARQUIVO ROBO"` no nome antes de ser enviado.
- [ ] Cada arquivo é enviado individualmente, assim que reservado — não há acúmulo em lote nem espera por outros arquivos.
- [ ] Autenticação contra a API da Ottimizza acontece uma única vez (não a cada arquivo); o token obtido é reaproveitado nos envios seguintes.
- [ ] Se o envio retornar 401, o robô reautentica (uma única vez) e reenvia o mesmo arquivo com o novo token; qualquer nova falha após isso é tratada como falha de envio, sem mais tentativas de autenticação.
- [ ] O envio é feito via multipart para o endpoint de storage do tenant correspondente.
- [ ] O marcador `" ARQUIVO ROBO"` é removido do nome do arquivo após a tentativa de envio, **tanto em sucesso quanto em falha** — o arquivo sempre termina com o nome original dentro de "ENVIADOS".
- [ ] Em caso de falha no envio, o erro é registrado no log de erros; além da renovação de token em caso de 401, não há nova tentativa automática.
- [ ] Falha ao ler um arquivo (antes da reserva) o move para uma pasta "ERROS" irmã da pasta "ENVIADOS", sem interromper o restante do ciclo.
- [ ] Falha ao processar uma pasta-alvo inteira não interrompe o processamento das demais pastas-alvo.

---

## 11. Glossário

| Termo | Significado |
|---|---|
| Contabilidade | O escritório de contabilidade cliente do Tareffa (tenant); identificado pelo parâmetro `CONTABILIDADE`. |
| Guia de conta paga | Documento/comprovante de pagamento que o robô localiza e envia. |
| Ciclo de varredura | Uma passada do robô pelas pastas-alvo já conhecidas, em busca de arquivos novos a enviar, repetida a cada `INTERVALO_VARREDURA_SEGUNDOS`. |
| Remapeamento | Varredura completa da árvore de pastas a partir da raiz, para atualizar a lista de pastas-alvo (clientes novos/removidos); repetida a cada `INTERVALO_REMAPEAMENTO_HORAS`, de forma independente do ciclo de varredura de arquivos. |
| Pasta-alvo | Pasta, em um nível específico de profundidade, onde se espera encontrar (direta ou indiretamente, via `PASTA_ENVIAR`) os arquivos de um cliente. |
| Pasta "ENVIAR" | Subpasta opcional (`PASTA_ENVIAR`) dentro da pasta-alvo, onde os arquivos pendentes de envio realmente ficam. |
| Pasta "ENVIADOS" | Pasta de backup para onde todo arquivo aceito é movido assim que localizado, antes mesmo da tentativa de envio. |
| Pasta "ERROS" | Pasta de quarentena para arquivos que não puderam ser lidos/preparados. |
| Marcador `" ARQUIVO ROBO"` | Sufixo inserido no nome do arquivo, dentro de "ENVIADOS", durante a janela entre a reserva e a tentativa de envio; removido logo em seguida, tanto em sucesso quanto em falha. |
| Nível / profundidade | Contagem de subpastas a partir da raiz (`PASTA_INICIAL`), que conta como nível 0. |
| Tenant | Cliente do Tareffa (mesmo conceito de "Contabilidade" acima), usado como segmentação no storage e na URL de upload. |
