package br.com.ottimizza.robo.diagnostico;

import br.com.ottimizza.robo.config.ArquivoChaveValor;
import br.com.ottimizza.robo.config.CredenciaisAuth;
import br.com.ottimizza.robo.config.Parametros;
import br.com.ottimizza.robo.config.ParametrosLoader;
import br.com.ottimizza.robo.discovery.DiagnosticoPastaInicial;
import br.com.ottimizza.robo.discovery.JanelaAnos;
import br.com.ottimizza.robo.discovery.MapaPastas;
import br.com.ottimizza.robo.discovery.MapaPastasLoader;
import br.com.ottimizza.robo.discovery.ObservadorVarredura;
import br.com.ottimizza.robo.discovery.PastaAlvoResolver;
import br.com.ottimizza.robo.discovery.TemplateMapa;
import br.com.ottimizza.robo.processamento.CaminhosPasta;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Modo de teste ({@code --testar-pastas}): mostra quais pastas o robo monitoraria com a
 * configuracao atual, e <b>por que</b> cada ramo foi podado.
 *
 * <p>Existe porque o modo de falha dominante deste produto e o silencio - o robo roda 24 h por dia
 * sem enviar nada e ninguem descobre. No Robo 1.0 as regras eram um bloco de Java editado na
 * maquina do cliente, e ele conviveu por anos em producao com uma condicao inalcancavel e dois
 * ramos que morriam sem alvo, sem ninguem notar. Sem uma forma de conferir, nem quem escreve a
 * regra consegue afirmar que ela faz o que pretende.
 *
 * <p><b>Invariante:</b> nada e escrito em disco. Nao le nem grava o cache de pastas-alvo (que mora
 * dentro da {@code PASTA_INICIAL}, ou seja, na arvore do cliente), nao usa o
 * {@code ErroLogger}, e nao cria nenhuma pasta. Quem grava o relatorio em arquivo e o
 * {@code testar.ps1}, por redirecionamento.
 *
 * <p>Toda a saida e ASCII: o console do Windows usa a codepage da maquina, e texto acentuado
 * sairia corrompido no arquivo que o suporte envia para a Ottimizza.
 */
public final class TestarPastas {

    public static final int OK = 0;
    public static final int ERRO_INESPERADO = 1;
    public static final int CONFIGURACAO_INVALIDA = 2;
    public static final int NENHUMA_PASTA_ALVO = 3;

    private static final int LIMITE_PODAS = 200;

    private TestarPastas() {
    }

    public static int executar(Path pastaInstalacao, PrintStream saida, boolean verboso) {
        try {
            return executarInterno(pastaInstalacao, saida, verboso);
        } catch (RuntimeException e) {
            saida.println("ERRO inesperado: " + e);
            return ERRO_INESPERADO;
        }
    }

    private static int executarInterno(Path pastaInstalacao, PrintStream saida, boolean verboso) {
        cabecalho(saida, pastaInstalacao);

        Path arquivoParametros = pastaInstalacao.resolve("parametros.txt");
        Optional<Parametros> parametrosOpt = ParametrosLoader.carregar(arquivoParametros);
        if (parametrosOpt.isEmpty()) {
            // ParametrosLoader devolve Optional.empty() sem motivo, o que e inutil num
            // diagnostico. A pre-checagem abaixo so le o arquivo para produzir a mensagem.
            saida.println("CONFIGURACAO INVALIDA em " + arquivoParametros);
            saida.println("  " + explicarParametrosInvalidos(arquivoParametros));
            rodape(saida);
            return CONFIGURACAO_INVALIDA;
        }
        Parametros parametros = parametrosOpt.get();

        imprimirConfiguracao(saida, parametros, pastaInstalacao);

        if (!diagnosticarPastaInicial(saida, parametros.getPastaInicial())) {
            rodape(saida);
            return CONFIGURACAO_INVALIDA;
        }

        saida.println();
        saida.println("--- VARREDURA (sem usar nem gravar cache) ---");
        Observador observador = new Observador(saida, verboso);
        PastaAlvoResolver.Resultado resultado = PastaAlvoResolver.simular(pastaInstalacao, parametros,
                observador, (mensagem, causa) -> saida.println("AVISO: " + mensagem
                        + (causa == null ? "" : " (" + causa + ")")));
        observador.encerrar();

        if (resultado instanceof PastaAlvoResolver.Resultado.ConfiguracaoInvalida invalida) {
            saida.println();
            saida.println("CONFIGURACAO INVALIDA: " + invalida.motivo());
            rodape(saida);
            return CONFIGURACAO_INVALIDA;
        }

        List<Path> pastasAlvo = ((PastaAlvoResolver.Resultado.Sucesso) resultado).pastasAlvo();
        imprimirPastasAlvo(saida, pastasAlvo, parametros);

        observador.imprimirRamosSemAlvo();

        saida.println();
        saida.println("Pastas visitadas: " + observador.visitadas
                + " | ramos podados: " + observador.podadas
                + " | ramos sem alvo: " + observador.totalDeRamosSemAlvo()
                + " | pastas-alvo: " + pastasAlvo.size());

        if (pastasAlvo.isEmpty()) {
            saida.println();
            saida.println("ATENCAO: nenhuma pasta-alvo encontrada - o robo nao enviaria nenhum arquivo.");
            saida.println("  Reveja PASTA_INICIAL e as regras acima. Se estiverem certas, envie este");
            saida.println("  relatorio para a Ottimizza.");
            rodape(saida);
            return NENHUMA_PASTA_ALVO;
        }

        rodape(saida);
        return OK;
    }

    // --------------------------------------------------------------------------------- secoes

    private static void cabecalho(PrintStream saida, Path pastaInstalacao) {
        saida.println("=======================================================================");
        saida.println(" MODO TESTE - nenhum arquivo sera movido, renomeado ou enviado.");
        saida.println("=======================================================================");
        saida.println("Pasta de instalacao: " + pastaInstalacao);
        saida.println("Rodando como usuario: " + System.getProperty("user.name", "?"));
        imprimirEstadoDasCredenciais(saida);
        saida.println();
    }

    /**
     * As credenciais viajam cifradas dentro do robo.jar, entao nao ha mais arquivo para o suporte
     * conferir a olho. Esta linha e o que resta: ela prova, na maquina do cliente e no runtime
     * jlink que o pacote leva, que o AES/GCM esta disponivel e que o blob decifra - sem imprimir
     * nenhum segredo. Sem ela, uma credencial mal colada so apareceria como falha de autenticacao
     * depois da instalacao inteira.
     */
    private static void imprimirEstadoDasCredenciais(PrintStream saida) {
        try {
            CredenciaisAuth.carregar();
            saida.println("Credenciais: OK (embutidas no robo.jar)");
        } catch (RuntimeException e) {
            saida.println("Credenciais: FALHA - " + e.getMessage());
        }
    }

    private static void rodape(PrintStream saida) {
        saida.println();
        saida.println("MODO TESTE - nada foi alterado.");
    }

    private static void imprimirConfiguracao(PrintStream saida, Parametros parametros,
                                              Path pastaInstalacao) {
        saida.println("--- CONFIGURACAO ---");
        saida.println("CONTABILIDADE  = " + parametros.getContabilidade());
        saida.println("PASTA_INICIAL  = " + parametros.getPastaInicial());
        saida.println("Modo           = " + PastaAlvoResolver.descreverModo(pastaInstalacao, parametros));

        Optional<MapaPastasLoader.Resultado> mapaOpt =
                PastaAlvoResolver.carregarMapa(pastaInstalacao, parametros);
        if (mapaOpt.isEmpty()) {
            saida.println("NIVEIS_SUBPASTA= " + parametros.getNiveisSubpasta());
            saida.println("CUSTOMIZACAO   = " + textoOuVazio(parametros.getCustomizacao()));
            saida.println("SUBNIVEL_ANO   = " + textoOuVazio(parametros.getSubnivelAno()));
            if (!parametros.getSubnivelAno().isBlank()) {
                saida.println("Anos aceitos   = " + JanelaAnos.criar(Year.now().getValue()).descricao()
                        + "   (fixo: ano corrente e o anterior)");
            }
            saida.println();
            saida.println("Nao existe " + MapaPastasLoader.NOME_ARQUIVO + " na pasta de instalacao,");
            saida.println("entao as pastas vem de NIVEIS_SUBPASTA + CUSTOMIZACAO.");
            return;
        }

        avisarChavesIgnoradas(saida, parametros);

        if (mapaOpt.get() instanceof MapaPastasLoader.Resultado.Carregado carregado) {
            MapaPastas mapa = carregado.mapa();
            if (mapa.usaTokenDeAno()) {
                JanelaAnos anos = JanelaAnos.criar(Year.now().getValue());
                saida.println("Anos aceitos   = " + anos.descricao()
                        + "   (fixo: ano corrente e o anterior)");
            }
            saida.println();
            saida.println("--- TEMPLATES (como foram interpretados) ---");
            for (TemplateMapa template : mapa.getTemplates()) {
                StringBuilder linha = new StringBuilder();
                for (int i = 0; i < template.profundidade(); i++) {
                    linha.append(i == 0 ? "" : " / ").append(template.segmento(i).textoOriginal());
                }
                saida.println("  linha " + template.linha() + " (nivel-alvo "
                        + template.profundidade() + "): " + linha);
            }
        }
    }

    private static void avisarChavesIgnoradas(PrintStream saida, Parametros parametros) {
        List<String> ignoradas = new ArrayList<>();
        if (parametros.getNiveisSubpasta() != 0) {
            ignoradas.add("NIVEIS_SUBPASTA=" + parametros.getNiveisSubpasta());
        }
        if (!parametros.getCustomizacao().isEmpty()) {
            ignoradas.add("CUSTOMIZACAO=" + parametros.getCustomizacao());
        }
        if (!parametros.getSubnivelAno().isEmpty()) {
            ignoradas.add("SUBNIVEL_ANO=" + parametros.getSubnivelAno());
        }
        if (ignoradas.isEmpty()) {
            return;
        }
        saida.println();
        saida.println("Ignorado por causa do mapa: " + String.join(", ", ignoradas));
    }

    /** @return {@code false} quando o caminho e invalido a ponto de nao valer varrer */
    private static boolean diagnosticarPastaInicial(PrintStream saida, String pastaInicial) {
        saida.println();
        saida.println("--- PASTA_INICIAL ---");

        if (!DiagnosticoPastaInicial.existe(pastaInicial)) {
            saida.println("NAO ENCONTRADA: " + pastaInicial);
            if (DiagnosticoPastaInicial.ehLetraDeUnidade(pastaInicial)) {
                saida.println("  " + DiagnosticoPastaInicial.dicaDeUnidadeMapeada(pastaInicial));
            } else {
                saida.println("  Confira se o caminho esta escrito corretamente e se voce tem acesso.");
            }
            return false;
        }

        saida.println("Encontrada: " + pastaInicial);
        if (DiagnosticoPastaInicial.podeSerUnidadeMapeada(pastaInicial)) {
            saida.println("  AVISO: " + DiagnosticoPastaInicial.avisoDeUnidadeMapeadaPresente(pastaInicial));
        }
        return true;
    }

    private static void imprimirPastasAlvo(PrintStream saida, List<Path> pastasAlvo, Parametros parametros) {
        saida.println();
        saida.println("--- PASTAS-ALVO (" + pastasAlvo.size() + ") ---");
        for (Path pasta : pastasAlvo) {
            saida.println("  " + pasta);
        }

        for (Path pasta : pastasAlvo) {
            if (contemPastaDeTrabalho(pasta, parametros)) {
                saida.println("  AVISO: \"" + pasta + "\" parece ser uma pasta de trabalho do proprio"
                        + " robo. Reveja o mapa: o robo passaria a reprocessar arquivos ja enviados.");
            }
        }
        for (Path pasta : pastasAlvo) {
            for (Path outra : pastasAlvo) {
                if (!pasta.equals(outra) && outra.startsWith(pasta)) {
                    saida.println("  AVISO: \"" + pasta + "\" contem outra pasta-alvo (\"" + outra
                            + "\"). Isso funciona, mas o cliente vera duas arvores de backup.");
                }
            }
        }
    }

    // ---------------------------------------------------------------------------- utilitarios

    private static boolean contemPastaDeTrabalho(Path pasta, Parametros parametros) {
        for (Path segmento : pasta) {
            String nome = segmento.toString();
            if (nome.equalsIgnoreCase(CaminhosPasta.NOME_ENVIADOS)
                    || nome.equalsIgnoreCase(CaminhosPasta.NOME_ERROS)
                    || (!parametros.getPastaEnviar().isBlank()
                        && nome.equalsIgnoreCase(parametros.getPastaEnviar()))) {
                return true;
            }
        }
        return false;
    }

    private static String explicarParametrosInvalidos(Path arquivoParametros) {
        Map<String, String> chaves = ArquivoChaveValor.ler(arquivoParametros);
        if (chaves.isEmpty()) {
            return "o arquivo nao existe, esta vazio ou nao pode ser lido.";
        }
        if (chaves.getOrDefault("CONTABILIDADE", "").trim().isEmpty()) {
            return "CONTABILIDADE esta vazia ou ausente.";
        }
        String pastaInicial = chaves.getOrDefault("PASTA_INICIAL", "").trim();
        if (pastaInicial.isEmpty()) {
            return "PASTA_INICIAL esta vazia ou ausente.";
        }
        if (!ParametrosLoader.pastaInicialAceitavel(pastaInicial)) {
            return "PASTA_INICIAL=\"" + pastaInicial + "\" nao e um caminho absoluto."
                    + " Use C:/Pasta, C:\\Pasta ou \\\\servidor\\pasta.";
        }
        String niveis = chaves.getOrDefault("NIVEIS_SUBPASTA", "").trim();
        if (niveis.isEmpty()) {
            return "NIVEIS_SUBPASTA esta vazio ou ausente.";
        }
        return "NIVEIS_SUBPASTA=\"" + niveis + "\" nao e um numero inteiro.";
    }

    private static String textoOuVazio(String valor) {
        return valor == null || valor.isEmpty() ? "(vazio)" : valor;
    }

    /** Imprime as decisoes da varredura em fluxo, para haver progresso visivel num share lento. */
    private static final class Observador implements ObservadorVarredura {

        private final PrintStream saida;
        private final boolean verboso;
        private final List<RamoSemAlvo> ramosSemAlvo = new ArrayList<>();
        private int visitadas;
        private int podadas;
        private int podasImpressas;

        Observador(PrintStream saida, boolean verboso) {
            this.saida = saida;
            this.verboso = verboso;
        }

        @Override
        public void pastaVisitada(Path pasta, int nivel) {
            visitadas++;
            if (verboso) {
                saida.println("  ".repeat(nivel + 1) + "entrou: " + nomeDe(pasta));
            }
        }

        @Override
        public void pastaPodada(Path pasta, int nivel, String motivo) {
            podadas++;
            if (verboso || podasImpressas < LIMITE_PODAS) {
                podasImpressas++;
                saida.println("  ".repeat(nivel) + "podado: " + nomeDe(pasta) + " -- " + motivo);
            }
        }

        @Override
        public void pastaAlvo(Path pasta, TemplateMapa template) {
            saida.println("ALVO: " + pasta + "   (template da linha " + template.linha() + ")");
        }

        private record RamoSemAlvo(Path pasta, String detalhe) {
        }

        @Override
        public void ramoSemAlvo(Path pasta, int nivel, String detalhe) {
            ramosSemAlvo.add(new RamoSemAlvo(pasta, detalhe));
        }

        void encerrar() {
            if (!verboso && podadas > podasImpressas) {
                saida.println("  ... e mais " + (podadas - podasImpressas)
                        + " ramos podados (use --verbose para ver todos).");
            }
        }

        /**
         * Ramos que o mapa aceitou mas que nao levaram a nenhuma pasta-alvo. Quase sempre indica
         * template com um nivel a mais ou a menos do que a arvore do cliente realmente tem.
         *
         * <p>So os ramos mais profundos sao listados: a recursao reporta o filho antes do pai, e um
         * ancestral so aparece na lista como consequencia do filho ja reportado.
         */
        void imprimirRamosSemAlvo() {
            List<RamoSemAlvo> maisProfundos = ramosSemAlvoMaisProfundos();
            if (maisProfundos.isEmpty()) {
                return;
            }
            saida.println();
            saida.println("--- RAMOS SEM PASTA-ALVO (" + maisProfundos.size() + ") ---");
            saida.println("Estas pastas foram aceitas pelo mapa, mas nenhuma pasta-alvo apareceu");
            saida.println("abaixo delas. Confira se o template tem um nivel a mais ou a menos:");
            for (RamoSemAlvo ramo : maisProfundos) {
                saida.println("  " + ramo.pasta() + " -- " + ramo.detalhe());
            }
        }

        private List<RamoSemAlvo> ramosSemAlvoMaisProfundos() {
            return ramosSemAlvo.stream()
                    .filter(ramo -> ramosSemAlvo.stream()
                            .noneMatch(outro -> !outro.pasta().equals(ramo.pasta())
                                    && outro.pasta().startsWith(ramo.pasta())))
                    .toList();
        }

        /** O mesmo numero que a secao de ramos sem alvo lista, para o resumo nao contradize-la. */
        int totalDeRamosSemAlvo() {
            return ramosSemAlvoMaisProfundos().size();
        }

        private static String nomeDe(Path pasta) {
            return pasta.getFileName() == null ? pasta.toString() : pasta.getFileName().toString();
        }
    }
}
