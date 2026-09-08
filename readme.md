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
| `PASTA_INICIAL` | Sim | Caminho absoluto: letra de unidade com qualquer barra (`C:/Tareffa`, `C:\Tareffa`) ou caminho UNC (`\\servidor\pasta`) | Pasta raiz a partir da qual a árvore de clientes é percorrida. O ciclo é ignorado se vazio ou se não for um caminho absoluto. Prefira UNC a unidade mapeada — ver seção 9. | `C:/Tareffa` |
| `NIVEIS_SUBPASTA` | Sim | Número inteiro | Profundidade (a partir da raiz, que conta como nível 0) em que ficam as pastas "alvo" de cada cliente. `0` = os arquivos ficam direto na raiz (modo pasta única, sem percorrer subpastas). O ciclo é ignorado se não for um inteiro válido. **Ignorado quando existe `mapa-pastas.txt`** (seção 6.6). | `2` |
| `INTERVALO_VARREDURA_SEGUNDOS` | Não (padrão sugerido: `30`) | Número inteiro (segundos) | Tempo de espera entre o fim de um ciclo de varredura de arquivos e o início do próximo. Não afeta a velocidade de remapeamento das pastas (ver `INTERVALO_REMAPEAMENTO_HORAS`). | `30` |
| `INTERVALO_REMAPEAMENTO_HORAS` | Não (padrão sugerido: `24`) | Número inteiro (horas) | De quanto em quanto tempo a lista de pastas-alvo é refeita do zero (varredura completa da árvore), para capturar pastas de clientes novas/removidas. Independente de `INTERVALO_VARREDURA_SEGUNDOS` — ver seção 6.2. | `24` |
| `PASTA_ENVIAR` | Não | Texto (nome de subpasta) | Nome de uma subpasta, dentro de cada pasta-alvo, onde os arquivos a enviar realmente ficam. Se vazio, os arquivos são lidos direto da pasta-alvo. A subpasta é criada automaticamente se não existir. | `ENVIAR` |
| `ENVIADO_DATADO` | Não | `SIM` (não sensível a maiúsculas) ou vazio/outro | Se `SIM`, a pasta de backup dos arquivos processados é organizada em `ENVIADOS/{ano}/{mês}` (data do **processamento**, não do documento). Caso contrário, usa uma única pasta `ENVIADOS/` sem subdivisão. | `SIM` |
| `CUSTOMIZACAO` | Não | Lista de regras separadas por `;`, cada uma no formato `nivel:operadorTermo` | Regras de exceção para a varredura de pastas quando a estrutura de um cliente foge do padrão. **Ignorado quando existe `mapa-pastas.txt`** (seção 6.6). Ver seção 6.3. | `1:>!INATIV;2:+FILIAL` |
| `SUBNIVEL_ANO` | Não | Número inteiro, `0`, ou texto contendo "ULTIM"/"ÚLTIM" | Nível em que existem pastas de ano no cliente, para filtrar quais anos o robô deve acessar. `0` ou "último/última" amarra o filtro ao nível-alvo final (após ajustes de `CUSTOMIZACAO`). **Ignorado quando existe `mapa-pastas.txt`** (seção 6.6). Ver seção 6.4. | `2` |

A **janela de anos** não é configurável: é sempre o **ano corrente e o anterior**. Ela vale nos dois modos — no filtro de `SUBNIVEL_ANO` (seção 6.4) e nos tokens `$ANO`/`$MES.ANO` do mapa de pastas (seção 6.6). A chave `VARIACAO_ANOS`, que existia para ajustar essa janela, foi removida: um `parametros.txt` que ainda a contenha continua carregando normalmente, apenas com uma linha de aviso no log dizendo que a chave é ignorada.

Além de `parametros.txt`, a pasta de instalação pode conter um segundo arquivo de configuração, **`mapa-pastas.txt`** — usado quando a estrutura de pastas do cliente é irregular demais para `NIVEIS_SUBPASTA` + `CUSTOMIZACAO` (por exemplo, ramos com profundidades diferentes sob a mesma raiz). Ele não é uma chave de `parametros.txt`: a simples presença do arquivo troca o modo de descoberta de pastas. Ver seção 6.6.

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
- `PASTA_INICIAL` estiver vazia ou não for um caminho absoluto (letra de unidade com barra, ou UNC com servidor **e** compartilhamento).

Como a configuração é recarregada a cada ciclo, uma correção no `parametros.txt` passa a valer no próximo ciclo, sem precisar reiniciar o serviço.

Uma marca de ordem de byte (BOM) no início do arquivo — que o Bloco de Notas insere ao salvar como "UTF-8 com BOM" — é descartada na leitura. Sem isso, a primeira chave do arquivo viria com um caractere invisível colado no nome e seria lida como ausente.

### 6.2 Determinação das pastas-alvo (cache de estrutura)

É importante separar dois conceitos que rodam em velocidades diferentes:

- **Varredura de arquivos** (a cada `INTERVALO_VARREDURA_SEGUNDOS`, ex.: 30 segundos): dentro de cada pasta-alvo **já conhecida**, procura arquivos novos e os envia. Isso roda sempre no intervalo curto — é o que garante que um arquivo seja enviado poucos segundos depois de ser colocado na pasta monitorada.
- **Remapeamento da árvore** (a cada `INTERVALO_REMAPEAMENTO_HORAS`, ex.: 24 horas): refaz a varredura completa da estrutura de pastas a partir da raiz, para descobrir **quais pastas existem** — isto é, detectar clientes novos, clientes removidos, ou mudanças que alterem quais pastas se encaixam nas regras de `CUSTOMIZACAO`/`SUBNIVEL_ANO`. Isso não precisa ser rápido: enquanto a lista de pastas-alvo não muda estruturalmente, o robô continua enviando arquivos das pastas já conhecidas normalmente, no ritmo da varredura de arquivos.

Ou seja: **o remapeamento controla apenas a integridade da lista de pastas monitoradas, nunca a velocidade de envio dos arquivos dentro delas.** Por isso o intervalo de remapeamento pode ser bem mais espaçado (ex.: uma vez por dia) sem qualquer impacto no tempo entre um arquivo ser adicionado pelo cliente e ser enviado.

Funcionamento:

- O resultado do remapeamento é **cacheado em disco**, junto com uma *impressão digital da configuração de descoberta*. O cache só é reaproveitado quando as duas condições valem: está dentro do prazo de `INTERVALO_REMAPEAMENTO_HORAS` **e** foi gerado pela mesma configuração que está em vigor agora.
- A impressão digital cobre exatamente o que muda a lista de pastas: `PASTA_INICIAL`, `NIVEIS_SUBPASTA`, `CUSTOMIZACAO`, `SUBNIVEL_ANO`, `PASTA_ENVIAR`, o conteúdo de `mapa-pastas.txt` e o **ano corrente**. Consequências práticas: uma regra corrigida passa a valer no próximo ciclo (segundos), sem reiniciar o serviço e sem apagar o cache à mão; e na virada de ano a lista é refeita imediatamente, em vez de ficar até `INTERVALO_REMAPEAMENTO_HORAS` usando a janela do ano anterior. Editar `ENVIADO_DATADO` ou os intervalos **não** dispara remapeamento, porque não afetam a descoberta.
- O log diz qual gatilho disparou: `Remapeando: cache vencido (24h)` ou `Remapeando: a configuracao de descoberta de pastas mudou`.
- Um cache gravado por uma versão anterior do robô não tem a impressão digital e é tratado como divergente: há **um** remapeamento extra no primeiro ciclo após a atualização, e daí em diante o regime é o normal.
- Quando um remapeamento termina com **zero pastas-alvo**, isso é registrado como aviso em `stdout` e no log de erros. É o modo de falha mais perigoso do robô — ele roda 24 h por dia sem enviar nada — e sem esse aviso o cenário é completamente silencioso.
- Se `NIVEIS_SUBPASTA = 0`, a lista é sempre a própria `PASTA_INICIAL` (modo pasta única, sem necessidade de cache/remapeamento).
- Se `NIVEIS_SUBPASTA > 0` e o cache estiver ausente ou vencido (primeira vez, ou passado o `INTERVALO_REMAPEAMENTO_HORAS` desde o último remapeamento), é feita uma varredura recursiva completa (seções 6.3/6.4) e o resultado é salvo em cache novamente.

### 6.3 Motor de regras de `CUSTOMIZACAO`

Usado quando a estrutura de pastas de um cliente foge do padrão configurado em `NIVEIS_SUBPASTA`. `NIVEIS_SUBPASTA` deve sempre representar o **menor** nível válido entre os clientes; exceções (clientes com um nível a mais) são tratadas com regras de customização.

Formato de cada regra: `nivel:operadorTermo`, várias regras separadas por `;`. O `nivel` é comparado à profundidade atual da pasta sendo avaliada durante a varredura (raiz = nível 0). O `termo` é comparado (contém, não sensível a maiúsculas, ignorando caracteres especiais) apenas contra o **nome da própria pasta** naquele nível — não o caminho completo.

> **Cuidado com a concordância.** Como o casamento é por *pedaço* do nome, `INATIVO` **não** alcança uma pasta chamada `EMPRESA INATIVA LTDA` — o final difere. Escreva o radical (`INATIV`), que cobre as duas formas. Este é o erro mais fácil de cometer e o mais difícil de perceber, porque o resultado é o robô processar uma pasta que deveria ignorar (ou ignorar uma que deveria processar), sem nenhuma mensagem. O modo de teste da seção 6.7 mostra o efeito real de cada regra.

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
`CUSTOMIZACAO = 1:>!INATIV;1:+CONSOLIDADO`

- `1:>!INATIV` — no nível 1, só entra em pastas cujo nome **não** contenha "INATIV" (o radical, para alcançar tanto `INATIVA` quanto `INATIVO`).
- `1:+CONSOLIDADO` — no nível 1, se o nome contiver "CONSOLIDADO", soma 1 ao nível-alvo apenas para esse ramo (passa de 2 para 3), fazendo o robô descer até a pasta `FILIAL SP/2025`.

Qualquer regra malformada (nível não numérico, ou termo que não comece com `+` ou `>`) marca toda a estrutura como inválida (ver seção 6.5).

### 6.4 Filtro de ano (`SUBNIVEL_ANO`)

Usado apenas quando o cliente **não** tem uma pasta dedicada de "enviar" e sua estrutura contém uma pasta por ano — sem esse filtro, o robô varreria e enviaria arquivos de anos antigos já processados manualmente.

- `SUBNIVEL_ANO` indica em qual nível da árvore está a pasta do ano. Se configurado como `0` ou contendo "ÚLTIMO/ÚLTIMA" (não sensível a maiúsculas/acentuação simples), o filtro passa a valer no **nível-alvo final** (útil combinado com regras de `CUSTOMIZACAO` que alteram a profundidade dinamicamente).
- A janela de anos aceitos é **fixa**: o ano corrente e o anterior. Não há chave para alterá-la. Na virada de ano a janela anda sozinha, e o remapeamento é disparado no mesmo ciclo porque o ano corrente entra na impressão digital do cache (seção 6.2).
- A checagem do ano é feita contra o **caminho completo** acumulado até aquele ponto (não apenas o nome da pasta do nível em questão) — isso permite estruturas "ano depois mês", mas também significa que um ano "aceito" que apareça em qualquer pasta ancestral do caminho já satisfaz a regra.

Exemplo: `SUBNIVEL_ANO = 2`, ano atual 2026 → nas pastas de ano do nível 2, apenas `2025` e `2026` são percorridas; `2024` e anteriores são ignoradas.

### 6.5 Configuração inválida

Se qualquer regra de `CUSTOMIZACAO` for malformada, ou se `mapa-pastas.txt` tiver qualquer problema (seção 6.6), o remapeamento marca a estrutura como inválida. Nesse caso:

- Nenhum arquivo é tocado neste ciclo.
- O cache anterior **não** é sobrescrito.
- O robô tenta remapear novamente a cada ciclo seguinte, até que a configuração seja corrigida.

Por que "ignorar o ciclo" é o comportamento certo, e não "usar o melhor palpite": as ações do robô são irreversíveis do ponto de vista do cliente. Um arquivo é **movido** para `ENVIADOS` antes do envio, as extensões de **todos** os arquivos da pasta de leitura são renomeadas, e pastas são criadas na árvore. Uma pasta-alvo errada, portanto, muta a árvore do cliente e tira documentos de onde uma pessoa espera encontrá-los — sem desfazer. O custo de pular um ciclo é limitado (a configuração é relida a cada ciclo, então a correção vale em segundos); o de varrer o lugar errado, não.

Como a configuração agora faz parte da impressão digital do cache (seção 6.2), uma alteração em `CUSTOMIZACAO`/`SUBNIVEL_ANO`/`mapa-pastas.txt` é validada e aplicada **no próximo ciclo**, sem esperar o próximo remapeamento e sem reiniciar o serviço.

### 6.6 Mapa de pastas (`mapa-pastas.txt`)

#### Motivação

`NIVEIS_SUBPASTA` + `CUSTOMIZACAO` descrevem bem uma árvore **regular**: todos os clientes na mesma profundidade, com no máximo algumas exceções pontuais. Elas não dão conta de uma árvore onde ramos diferentes têm profundidades diferentes, onde a condição depende de pastas ancestrais, ou onde há pastas de mês/ano no meio do caminho.

Esse caso é comum, e historicamente era resolvido **escrevendo código na máquina do cliente** — um bloco de condicionais por nível de profundidade, editado à mão em cada instalação. O custo era alto e invisível: cópias divergentes por cliente, nada sob controle de versão, e nenhuma forma de conferir o resultado. Uma dessas cópias perdeu uma única linha e transformou ~80 linhas de regra em código morto sem ninguém perceber; outra carregava uma condição inalcançável e dois ramos que morriam sem pasta-alvo, em produção, por anos.

A observação que resolve o problema: quem escrevia aquele bloco **primeiro documentava a árvore do cliente em comentário**, numa notação declarativa (um caminho por linha, com curingas para "todas as pastas" e marcadores para ano e mês.ano) e depois traduzia isso à mão para condicionais. O mapa de pastas elimina a tradução: **a lista de caminhos passa a ser executada diretamente.**

#### Formato

Um arquivo `mapa-pastas.txt` na pasta de instalação (ao lado de `robo.jar` e `parametros.txt`), com **uma linha por caminho**. Os caminhos partem da `PASTA_INICIAL` (nível 0); o segmento *i* casa com o nome da pasta no nível *i*. A **última** pasta de cada linha é a pasta-alvo daquele caminho.

- Segmentos separados por `/` **ou** `\` — nenhum dos dois pode aparecer em nome de pasta no Windows, então aceitar os dois não gera ambiguidade, e o suporte pode colar caminhos copiados do Explorer.
- Uma linha pode começar pelo caminho absoluto do cliente (`W:/Departamento Fiscal/...`); nesse caso a raiz é **conferida** contra `PASTA_INICIAL` e removida. Divergência é erro, não descarte silencioso.
- Linha iniciada por `#` é comentário; linha vazia é ignorada. **Não existe comentário no meio da linha** — assim uma pasta chamada `Guias # 2026` continua sendo escrevível.
- Cada segmento é uma lista de **alternativas** separadas por `@`.
- O separador de alternativas já foi `|`. Um mapa na sintaxe antiga é **recusado com mensagem explícita** (`"|" nao e mais o separador de alternativas - use "@"`), e não interpretado como texto literal — `|` é ilegal em nome de pasta no Windows, então dá para distinguir o caso com certeza e falhar alto em vez de silenciosamente deixar de casar as pastas.

> **Regra de casamento do segmento:** a pasta casa o segmento se casar **ao menos uma alternativa positiva** e **não casar nenhuma negativa**. Havendo só alternativas negativas, existe um `*` positivo implícito.

Dentro de um segmento, portanto, as alternativas **positivas** se combinam como **OU** e as **negativas** como **E** (basta uma negativa casar para a pasta ser rejeitada). É a mesma semântica dos operadores `>`/`>!` de `CUSTOMIZACAO` (seção 6.3) — o suporte não precisa aprender um segundo modelo mental para o segmento.

| Alternativa | Casa |
|---|---|
| `Nome` | qualquer pasta cujo nome **contenha** esse texto, ignorando acentos, pontuação, espaços e caixa (`Declaracao Estaduais` casa `Declaração Estaduais`; `DCTFWEB - MIT` casa `DCTFWEB-MIT`) |
| `=Nome` | só o nome **exato**, na mesma comparação normalizada (`=ECD` não casa `ECD ANTIGO`) |
| `*` | qualquer pasta daquele nível |
| `!Nome` (ou `*!Nome`) | todas as pastas do nível **menos** as que contêm esse texto |
| `A@B@C` | `A` ou `B` ou `C` |
| `$ANO` | pasta cujo nome tem exatamente um grupo de 4 dígitos, e esse ano é o corrente ou o anterior |
| `$MES.ANO` | pasta de mês e ano (`01.2026`, `12-2025`, `012026`, `1.2026`), com o ano na janela |
| `$ANO.MES` | o inverso (`2026.01`) |

> ⚠️ **Duas exclusões precisam ficar no mesmo segmento, não em linhas separadas.** Este é o erro mais fácil de cometer no mapa, e ele *amplia* a seleção em vez de restringi-la. Para excluir `DMED` **e** `TESTE` num nível, escreva as duas negativas no mesmo segmento:
>
> ```
> .../Declaracao Estaduais/!DMED@!TESTE/$ANO/$MES.ANO/*
> ```
>
> Quebrar isso em duas linhas **anula a exclusão inteira**:
>
> ```
> .../Declaracao Estaduais/!DMED/$ANO/$MES.ANO/*     <- esta linha aceita TESTE
> .../Declaracao Estaduais/!TESTE/$ANO/$MES.ANO/*    <- e esta aceita DMED
> ```
>
> Porque **linhas diferentes são caminhos independentes e se somam como OU**: cada pasta excluída por uma linha continua sendo aceita pela outra, e o resultado final é a união. Note que aqui a regra é o **oposto** da `CUSTOMIZACAO`, onde duas regras `>` do mesmo nível se combinam como E — `1:>!INATIV;1:>!TESTE` de fato exclui as duas. A transferência dessa intuição para o mapa é a armadilha. Em resumo: **dentro do segmento, E; entre linhas, OU.**

Por que o casamento de texto é por **substring** e não por igualdade: a normalização já elimina o ruído de acentos e pontuação, então igualdade seria viável — mas o modo de falha dela é o pior possível aqui. Escreve-se `Tributos`, a pasta chama-se `Tributos Municipais`, e o resultado seria **zero pastas-alvo e silêncio**. Com substring funciona; o preço é o falso-positivo (`MIT` alcançando `ADMITIDOS`), que é *visível* no modo de teste e tem escape explícito (`=MIT`). Vale a mesma ressalva de concordância da seção 6.3: prefira o radical ao singular.

Já `$ANO` é uma afirmação sobre **aquele segmento**, diferente do filtro da seção 6.4, que procura o ano no caminho acumulado inteiro. É a forma precisa, e é o motivo de `SUBNIVEL_ANO` não ser necessário no modo mapa.

Ao contrário de `/`, `\` e do antigo `|`, o `@` **é permitido** em nome de pasta no Windows. A consequência é que uma pasta com `@` no nome (por exemplo `integracao@escritorio`) escrita como texto literal seria dividida em duas alternativas, virando um OU mais permissivo do que se pretendia. O efeito aparece como uma pasta-alvo inesperada no modo de teste (seção 6.7), que é onde se pega isso.

Fora esse caso, não há mecanismo de escape, e isso é deliberado: o Windows proíbe `* | : \ / ? " < >` em nomes de pasta, e os caracteres `$ ! =` só têm significado especial como **primeiro** caractere de uma alternativa — e são descartados pela normalização, então uma pasta chamada `!URGENTE` é alcançada escrevendo simplesmente `URGENTE`.

**`**` (qualquer número de níveis) não é suportado** e é recusado no parse com mensagem explícita. Sem ele vale a invariante "índice do segmento = nível atual", o que mantém o estado da varredura mínimo e — mais importante — limita a profundidade da recursão ao maior template, tornando-a imune a *junctions* e links cíclicos, que a listagem de diretórios seguiria.

#### Precedência

> **Se `mapa-pastas.txt` existe, ele é a única fonte da descoberta de pastas.** `NIVEIS_SUBPASTA`, `CUSTOMIZACAO` e `SUBNIVEL_ANO` passam a ser ignorados (cada chave preenchida gera uma linha de aviso no log). Os tokens `$ANO`/`$MES.ANO` usam a janela fixa de anos (corrente e anterior). `CONTABILIDADE`, `PASTA_ENVIAR`, `ENVIADO_DATADO` e os dois intervalos continuam valendo integralmente — são sobre arquivos, não sobre pastas.

Combinar duas linguagens de seleção de pasta produziria uma tabela-verdade que ninguém consegue simular de cabeça, com modo de falha invisível. Uma chave, uma linguagem.

Detalhes que decorrem disso:

- `NIVEIS_SUBPASTA` continua sendo **obrigatório e válido** em `parametros.txt` — apenas ignorado. Consequência boa: para ligar o modo mapa não é preciso editar `parametros.txt`, basta colocar o arquivo na pasta.
- A decisão de modo vem **antes** do curto-circuito de `NIVEIS_SUBPASTA = 0`: com o mapa presente, o mapa ganha mesmo assim.
- Para desligar o mapa, renomeie o arquivo para `mapa-pastas.txt.off`.
- O nome do arquivo é fixo; não existe uma chave para apontar para outro caminho. A presença do arquivo *é* a chave.
- A linha de log de cada ciclo informa o modo em vigor: `modo=MAPA (mapa-pastas.txt, 7 templates)` ou `modo=NIVEIS (niveis=2 customizacao=...)`.

#### Ambiguidade: dois caminhos que terminam em níveis diferentes no mesmo ramo

**Os dois são pasta-alvo.** O critério não é estético, é **monotonicidade**: acrescentar uma linha ao mapa só pode *adicionar* pastas-alvo, nunca remover. Assim o suporte amplia a cobertura sem risco de quebrar o que já funcionava. As alternativas ("o mais raso ganha", "o mais profundo ganha") destroem essa propriedade e criam interação à distância entre linhas.

Consequência a comunicar ao cliente: com pastas-alvo aninhadas e `ENVIADO_DATADO=SIM`, aparecem duas árvores de backup na mesma sub-árvore. Não há envio em duplicidade — a busca de arquivos dentro da pasta-alvo não é recursiva —, e o modo de teste avisa quando uma pasta-alvo contém outra.

#### Pastas de trabalho do robô

`ENVIADOS`, `ERROS` e a `PASTA_ENVIAR` configurada **nunca** são alcançadas por `*`, `!termo` ou pelos tokens de ano. Só um segmento que as nomeie explicitamente (`ENVIADOS` ou `=ENVIADOS`) as alcança — uma escolha consciente de quem escreve o mapa.

Sem essa regra, o mapa se autoenvenena: um caminho terminado em `*` faz uma pasta-alvo qualquer criar `ENVIADOS/` (e, com `ENVIADO_DATADO=SIM`, `ENVIADOS/2026/09` — onde `2026` casaria `$ANO`), e no remapeamento seguinte as pastas de backup do próprio robô entram na lista de pastas-alvo, levando-o a criar `ENVIADOS/ENVIADOS/...` e a reprocessar arquivos já enviados.

#### Validação

Qualquer problema torna a configuração inválida (seção 6.5), e a mensagem sempre diz **arquivo, número de linha, texto ofensor e o que era esperado**:

```
mapa-pastas.txt linha 4: token desconhecido "$AN0" (esperado $ANO, $MES.ANO ou $ANO.MES)
mapa-pastas.txt linha 7: "**" nao e suportado - escreva um * por nivel
mapa-pastas.txt linha 2: template parte de "X:/", mas PASTA_INICIAL e "W:/"
mapa-pastas.txt: nenhum template (todas as linhas sao comentario ou vazias)
```

São inválidos: token `$` desconhecido; `**`; segmento vazio (duas barras seguidas); alternativa vazia (`A@@B`); `=` ou `!` sem termo; `!*`; raiz absoluta divergente de `PASTA_INICIAL`; e os dois casos abaixo, que merecem justificativa por serem contraintuitivos:

- **Arquivo ilegível é inválido, não "ausente".** Tratar falha de leitura como ausência faria o robô cair em silêncio no modo `NIVEIS_SUBPASTA` e varrer pastas erradas.
- **Arquivo que existe mas não rende nenhum template é inválido, não um retorno ao modo antigo.** Pelo mesmo motivo. É também por isso que o `.example` tem **todas** as linhas comentadas: uma cópia acidental produz um erro alto e específico, não uma varredura silenciosa do lugar errado.

Há um teto de 200 caminhos e 20 níveis por caminho, como defesa contra uma listagem de pastas colada por acidente dentro do arquivo.

#### Exemplo trabalhado

Estrutura de um cliente real (a `PASTA_INICIAL` aponta para a raiz que contém os departamentos):

```
<PASTA_INICIAL>
├── Departamento Fiscal
│   ├── Tributos
│   │   └── <empresa>/<filial>/<ano>/GUIAS/<mes.ano>          ← alvo, nível 7
│   └── Protocolos
│       ├── Declaracao Estaduais
│       │   ├── <tipo, menos DMED>/<ano>/<mes.ano>/<pasta>    ← alvo, nível 7
│       │   └── DMED/<ano>                                    ← alvo, nível 5
│       └── Declaracao Federais   (mesma forma)
└── Departamento Contabil
    └── Declaracoes RFB
        ├── DCTFWEB - MIT/<mes.ano>/<pasta>                   ← alvo, nível 5
        └── ECD@ECF@IBGE@SIMPLES/<ano>/1.Recibos        ← alvo, nível 5
```

O mapa correspondente — sete linhas, com pastas-alvo em dois níveis diferentes (5 e 7):

```
Departamento Fiscal/Tributos/*/*/$ANO/GUIAS/$MES.ANO
Departamento Fiscal/Protocolos/Declaracao Estaduais/!DMED/$ANO/$MES.ANO/*
Departamento Fiscal/Protocolos/Declaracao Estaduais/DMED/$ANO
Departamento Fiscal/Protocolos/Declaracao Federais/!DMED/$ANO/$MES.ANO/*
Departamento Fiscal/Protocolos/Declaracao Federais/DMED/$ANO
Departamento Contabil/Declaracoes RFB/DCTFWEB - MIT/$MES.ANO/*
Departamento Contabil/Declaracoes RFB/ECD@ECF@IBGE@SIMPLES/$ANO/1.Recibos
```

Os tokens `$ANO` e `$MES.ANO` aceitam o ano corrente e o anterior.

Note o que **não** precisou ser escrito: nenhuma condição sobre pastas ancestrais, nenhuma soma de profundidade, nenhuma numeração de nível. A posição do segmento na linha já diz o nível, e a linha inteira já diz o contexto. É por isso que a transcrição é mais confiável que a tradução para condicionais: um nível novo no meio da árvore do cliente é a inserção de **um segmento em uma linha**, não a renumeração de dez regras.

### 6.7 Modo de teste (`--testar-pastas`)

`robo.jar --testar-pastas` mostra quais pastas o robô monitoraria com a configuração atual, e **por que** cada ramo entrou ou foi descartado. Na prática o suporte roda `testar.bat`, que faz isso e salva o relatório em `logs/`.

Existe porque o modo de falha dominante deste robô é o silêncio — ele roda 24 h por dia sem enviar nada e ninguém descobre. Sem uma forma de conferir, nem quem escreve a regra consegue afirmar que ela faz o que pretende; a evidência é o histórico descrito na seção 6.6.

**Invariante: nada é escrito em disco pelo robô nesse modo.** O cache de pastas-alvo não é lido nem gravado (ele mora dentro da `PASTA_INICIAL`, isto é, na árvore do cliente), o log de erros não é usado e nenhuma pasta é criada. Quem grava o relatório em arquivo é o script, por redirecionamento. As credenciais não são pré-requisito para rodá-lo: elas vêm embutidas no `robo.jar` e o relatório apenas **reporta** o estado delas numa linha (`Credenciais: OK`), sem imprimir nenhum valor. Uma falha ali não impede a conferência das pastas.

O relatório traz, em ordem: a configuração lida; o modo em vigor e a janela de anos; o **eco de cada caminho do mapa como foi interpretado** (para se ver que `$MES.ANO` foi reconhecido como token, e não como texto literal); o diagnóstico da `PASTA_INICIAL`; cada decisão da varredura com o motivo (`nenhum template aceita "2019" neste nivel - esperado $ANO (anos aceitos: 2025..2026)`, `pasta de trabalho do robo (ENVIADOS)`); a lista de pastas-alvo; os **ramos sem pasta-alvo**; e um resumo.

A seção de ramos sem pasta-alvo é o diagnóstico que faltava: são pastas que o mapa aceitou, mas onde nenhuma pasta-alvo apareceu abaixo — quase sempre um caminho com um nível a mais ou a menos do que a árvore realmente tem. É exatamente o defeito que passou anos invisível em produção.

Códigos de saída: `0` tudo certo · `2` configuração inválida · `3` configuração válida mas **nenhuma** pasta-alvo · `1` erro inesperado · `64` uso incorreto. `--verbose` mostra também cada pasta visitada e remove o limite de linhas de descarte.

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

**Onde esses quatro valores moram.** Como são os mesmos em toda instalação, deixaram de ser configuração: viajam **cifrados (AES-256-GCM) dentro do próprio `robo.jar`** e são decifrados no startup. O arquivo `credenciais.properties`, que os expunha em texto puro na pasta de instalação, não existe mais — nem no pacote, nem no instalador, nem no guia. Sobra um único override, as variáveis de ambiente `ROBO_AUTH_*`, para apontar uma execução de desenvolvimento a outro servidor sem recompilar.

Para trocar qualquer um dos valores (rotação de senha, outro ambiente), rode o gerador — `GerarCredenciaisEmbutidas`, que vive em `src/test/java` e por isso **não** vai no jar do cliente —, cole o bloco impresso em `CredenciaisEmbutidas.java` e remonte o pacote.

> **O alcance dessa proteção.** Isto é ofuscação, não sigilo. A chave viaja no mesmo jar que o texto cifrado: quem tiver o jar e um descompilador recupera os valores. Nenhum esquema muda isso — o programa precisa decifrar para usar, então tudo que ele precisa está ali. O que se ganha é real e era o objetivo: a senha de integração deixa de estar legível num `.txt` ao lado do robô, ao alcance de qualquer pessoa que abra a pasta de instalação, um backup dela ou o compartilhamento de rede por onde o pacote é distribuído. O que **não** se ganha é sigilo contra um cliente decidido a extrair a credencial — para isso, a credencial teria de nunca chegar à máquina dele (ver seção 9, ponto 1).

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

1. **Autenticação com credencial fixa e igual para todos os tenants** (seção 7.3) — a senha usada para todo tenant é um valor literal fixo, não um segredo individual gerido com segurança. Embuti-la cifrada no jar (seção 7.3) resolveu a exposição *casual* — ela não fica mais legível num arquivo na máquina do cliente — mas **não** o problema de fundo: a credencial continua chegando à máquina dele e continua sendo a mesma para todos. Um único vazamento afeta todos os tenants, e não há como revogar uma instalação isoladamente. O caminho de saída é um token por instalação, emitido pelo servidor e revogável individualmente.
2. **Sem identificador estável por arquivo** além do próprio nome — dificulta correlacionar, no log, diferentes tentativas de envio do mesmo arquivo. Vale considerar um identificador estável (ex.: hash do conteúdo) só para fins de log/diagnóstico.
3. A proteção contra arquivos "Thumbs.db" (arquivo de miniaturas do Windows) é sensível a maiúsculas/minúsculas e só cobre alguns formatos de grafia do nome — vale revisar com um casamento não sensível a caixa se esse filtro ainda for necessário.
4. **Unidade mapeada em `PASTA_INICIAL`.** Um serviço do Windows **não enxerga unidades de rede mapeadas** (`W:\...`), mesmo rodando sob uma conta de domínio — apenas caminhos UNC (`\\servidor\pasta`) funcionam de forma confiável. O sintoma é o pior possível: a listagem da pasta falha, o remapeamento devolve zero pastas-alvo e o robô roda indefinidamente sem enviar nada. O robô detecta o caso e registra a orientação em log; o modo de teste (seção 6.7) avisa que rodou sob o usuário, e não sob a conta do serviço; e o `testar.ps1` consulta o Windows e **sugere o UNC equivalente** para colar no `parametros.txt`. A conversão nunca é automática: o mapeamento pode simplesmente não existir sob a conta do serviço, então a decisão é de quem instala.
5. **O remapeamento bloqueia o ciclo.** O robô é single-thread, então uma varredura completa em compartilhamento de rede lento (milissegundos por pasta, minutos numa árvore de dezenas de milhares) atrasa o envio de arquivos enquanto acontece. Isso sempre foi verdade; com a invalidação de cache por configuração (seção 6.2) a frequência aumenta, porque cada ajuste de regra dispara um remapeamento. Se isso incomodar, o caminho é remapear em segundo plano servindo o cache antigo nesse intervalo.
6. **O mapa de pastas descreve a forma da árvore.** Isso troca o risco "varre a pasta errada" pelo risco "**não** varre uma pasta nova": um departamento criado depois, que nenhum caminho do mapa cobre, é ignorado sem aviso. Mitigações: `*` está sempre disponível para deixar um nível aberto, e o modo de teste torna a conferência periódica barata.

---

## 10. Checklist de Regras de Negócio (para conferência da reescrita)

**Configuração e validação**
- [ ] `CONTABILIDADE`, `PASTA_INICIAL` e `NIVEIS_SUBPASTA` são obrigatórios; ausência/valor inválido de qualquer um deles faz o ciclo ser ignorado, sem processar nada.
- [ ] `PASTA_ENVIAR`, `ENVIADO_DATADO`, `CUSTOMIZACAO`, `SUBNIVEL_ANO`, `INTERVALO_VARREDURA_SEGUNDOS` e `INTERVALO_REMAPEAMENTO_HORAS` são opcionais, com os efeitos descritos na seção 3.
- [ ] A janela de anos é sempre o ano corrente e o anterior, sem chave que a configure; um `VARIACAO_ANOS` remanescente é ignorado com aviso, sem invalidar o ciclo.
- [ ] A configuração é recarregada a cada ciclo (não apenas na inicialização do serviço).

**Descoberta de pastas**
- [ ] `NIVEIS_SUBPASTA = 0` = modo pasta única (sem varredura, sem regras de customização/ano aplicáveis) — **exceto** se existir `mapa-pastas.txt`, que ganha mesmo assim.
- [ ] A varredura de arquivos (a cada `INTERVALO_VARREDURA_SEGUNDOS`) e o remapeamento da árvore de pastas (a cada `INTERVALO_REMAPEAMENTO_HORAS`) são independentes — o remapeamento nunca atrasa o envio de um arquivo em uma pasta já conhecida.
- [ ] A lista de pastas-alvo é cacheada em disco entre remapeamentos e recalculada quando o cache está ausente, vencido **ou foi gerado por outra configuração de descoberta** (incluindo outro ano corrente).
- [ ] Um cache sem impressão digital (gravado por versão anterior) provoca exatamente um remapeamento extra, e não é tratado como válido.
- [ ] Regras `+`/`+!`/`>`/`>!` de `CUSTOMIZACAO` funcionam conforme a tabela da seção 6.3, casando por substring normalizada do nome da pasta.
- [ ] Filtro de ano (`SUBNIVEL_ANO`) funciona conforme a seção 6.4.
- [ ] Configuração de regra inválida faz o remapeamento ser descartado e tentado de novo no próximo ciclo/remapeamento, sem sobrescrever o cache anterior.
- [ ] Remapeamento com zero pastas-alvo gera aviso em `stdout` e no log de erros.
- [ ] `PASTA_INICIAL` aceita `C:/Pasta`, `C:\Pasta` e `\\servidor\pasta`; rejeita caminho relativo, `C:` puro e UNC sem compartilhamento.

**Mapa de pastas (seção 6.6)**
- [ ] A presença de `mapa-pastas.txt` faz `NIVEIS_SUBPASTA`, `CUSTOMIZACAO` e `SUBNIVEL_ANO` serem ignorados (com aviso por chave preenchida).
- [ ] Segmentos casam conforme a tabela: texto por substring, `=` exato, `*`, `!termo`, `A@B@C`, `$ANO`, `$MES.ANO`, `$ANO.MES`.
- [ ] Uma pasta casa o segmento se casar ao menos uma alternativa positiva e nenhuma negativa; só negativas implicam um `*` positivo.
- [ ] Caminhos com profundidades diferentes coexistem, e dois caminhos terminando em níveis diferentes no mesmo ramo produzem **duas** pastas-alvo.
- [ ] `ENVIADOS`, `ERROS` e `PASTA_ENVIAR` nunca são alcançadas por curinga, negação ou token de ano; são alcançadas por segmento de texto explícito.
- [ ] `**` é recusado no parse; separadores `/` e `\` são equivalentes; `#` no início da linha é comentário.
- [ ] Raiz absoluta no template é conferida contra `PASTA_INICIAL` e removida; divergência é erro.
- [ ] Arquivo ilegível ou sem nenhum caminho válido é configuração inválida, nunca um retorno silencioso ao modo `NIVEIS_SUBPASTA`.
- [ ] Mensagem de erro sempre traz arquivo, número de linha e o que era esperado.

**Modo de teste (seção 6.7)**
- [ ] `--testar-pastas` não escreve nada em disco: não lê nem grava o cache, não usa o log de erros, não cria pastas.
- [ ] Roda mesmo que as credenciais embutidas falhem, reportando `Credenciais: FALHA` sem interromper a conferência das pastas, e nunca imprime o valor de uma credencial.
- [ ] Ecoa os caminhos do mapa como foram interpretados, e o motivo de cada descarte.
- [ ] Lista os ramos aceitos que não chegaram a nenhuma pasta-alvo.
- [ ] Códigos de saída: `0` ok, `2` configuração inválida, `3` zero pastas-alvo, `1` erro inesperado, `64` uso incorreto.
- [ ] Um argumento desconhecido imprime o uso e sai com `64`, sem iniciar o laço do serviço.

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
| Mapa de pastas | Arquivo `mapa-pastas.txt` com um caminho por linha, descrevendo a estrutura de pastas do cliente. Quando existe, é a única fonte da descoberta de pastas (seção 6.6). |
| Impressão digital da configuração | Resumo dos parâmetros que determinam quais pastas são monitoradas, guardado junto com o cache para detectar que a configuração mudou (seção 6.2). |
| Ramo sem pasta-alvo | Pasta aceita pelo mapa a partir da qual nenhuma pasta-alvo foi alcançada — sinal de que o caminho descrito tem um nível a mais ou a menos que a árvore real (seção 6.7). |
| Tenant | Cliente do Tareffa (mesmo conceito de "Contabilidade" acima), usado como segmentação no storage e na URL de upload. |
