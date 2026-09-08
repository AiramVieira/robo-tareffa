package br.com.ottimizza.robo.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A arvore usada aqui e a do cliente real que sai do Robo 1.0, transcrita do bloco
 * {@code REGRAS MANUAIS} do {@code .script} (que era uma compilacao manual, e com bugs, desta
 * mesma lista de caminhos).
 */
class MapaScannerTest {

    /** Transcricao do comentario que documentava a arvore no Robo 1.0. */
    private static final String MAPA_CLIENTE_REAL = """
            Departamento Fiscal/Tributos/*/*/$ANO/GUIAS/$MES.ANO
            Departamento Fiscal/Protocolos/Declaracao Estaduais/!DMED/$ANO/$MES.ANO/*
            Departamento Fiscal/Protocolos/Declaracao Estaduais/DMED/$ANO
            Departamento Fiscal/Protocolos/Declaracao Federais/!DMED/$ANO/$MES.ANO/*
            Departamento Fiscal/Protocolos/Declaracao Federais/DMED/$ANO
            Departamento Contabil/Declaracoes RFB/DCTFWEB - MIT/$MES.ANO/*
            Departamento Contabil/Declaracoes RFB/ECD@ECF@IBGE@SIMPLES/$ANO/1.Recibos
            """;

    private static final JanelaAnos ANOS_2025_2026 = JanelaAnos.criar(2026);

    private static final Set<String> RESERVADOS = Set.of(
            CustomizacaoRule.normalizar("ENVIADOS"), CustomizacaoRule.normalizar("ERROS"));

    // ---------------------------------------------------------------- infraestrutura do fixture

    private static void criar(Path raiz, String... caminhosRelativos) throws IOException {
        for (String caminho : caminhosRelativos) {
            Files.createDirectories(raiz.resolve(caminho.replace('/', java.io.File.separatorChar)));
        }
    }

    private static MapaPastas mapa(String texto) {
        MapaPastasLoader.Resultado resultado = MapaPastasLoader.interpretar(texto, null);
        return assertInstanceOf(MapaPastasLoader.Resultado.Carregado.class, resultado).mapa();
    }

    private static Set<String> relativos(Path raiz, List<Path> pastas) {
        return pastas.stream()
                .map(p -> raiz.relativize(p).toString().replace('\\', '/'))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<Integer> profundidades(Path raiz, List<Path> pastas) {
        return pastas.stream()
                .map(p -> raiz.relativize(p).getNameCount())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static final class ObservadorGravador implements ObservadorVarredura {
        final List<String> visitadas = new ArrayList<>();
        final List<String> podadas = new ArrayList<>();
        final List<String> motivos = new ArrayList<>();

        @Override
        public void pastaVisitada(Path pasta, int nivel) {
            visitadas.add(pasta.getFileName() == null ? "<raiz>" : pasta.getFileName().toString());
        }

        @Override
        public void pastaPodada(Path pasta, int nivel, String motivo) {
            podadas.add(pasta.getFileName().toString());
            motivos.add(motivo);
        }
    }

    /**
     * A arvore do cliente, com iscas: anos fora da janela, mes invalido, um DMED sob Federais, um
     * departamento que nenhum template menciona, e uma pasta de backup do proprio robo.
     */
    private static void montarArvoreDoClienteReal(Path raiz) throws IOException {
        criar(raiz,
                // --- alvos esperados
                "Departamento Fiscal/Tributos/EMPRESA A/FILIAL 1/2026/GUIAS/03.2026",
                "Departamento Fiscal/Protocolos/Declaracao Federais/IRPF/2026/02.2026/ANEXOS",
                "Departamento Fiscal/Protocolos/Declaracao Estaduais/DMED/2025",
                "Departamento Contabil/Declaracoes RFB/DCTFWEB - MIT/01.2026/RECIBOS",
                "Departamento Contabil/Declaracoes RFB/ECD/2026/1.Recibos",
                "Departamento Contabil/Declaracoes RFB/ECF/2025/1.Recibos",
                // --- iscas que devem ser podadas
                "Departamento Fiscal/Tributos/EMPRESA A/FILIAL 1/2026/GUIAS/03.2019",
                "Departamento Fiscal/Tributos/EMPRESA A/FILIAL 1/2019/GUIAS/03.2026",
                "Departamento Fiscal/Tributos/EMPRESA A/FILIAL 1/2026/EXTRATOS",
                "Departamento Fiscal/Protocolos/Declaracao Estaduais/DMED/2019",
                "Departamento Fiscal/Protocolos/OUTRA COISA",
                "Departamento Contabil/Declaracoes RFB/DCTFWEB - MIT/RASCUNHO/RECIBOS",
                "Departamento Contabil/Declaracoes RFB/ECD/2019/1.Recibos",
                "Departamento Pessoal/FOLHA/2026",
                "EMPRESA INATIVO LTDA/2026",
                // --- chamariz: pasta de trabalho do proprio robo em posicao de curinga
                "Departamento Contabil/Declaracoes RFB/DCTFWEB - MIT/01.2026/ENVIADOS/2026/09");
    }

    // ---------------------------------------------------------------------------------- testes

    @Test
    void resolveAArvoreDoClienteRealComProfundidadesDiferentesPorRamo(@TempDir Path raiz) throws IOException {
        montarArvoreDoClienteReal(raiz);

        List<Path> alvos = new MapaScanner()
                .escanear(raiz, mapa(MAPA_CLIENTE_REAL), ANOS_2025_2026, RESERVADOS);

        assertEquals(new TreeSet<>(Set.of(
                        "Departamento Contabil/Declaracoes RFB/DCTFWEB - MIT/01.2026/RECIBOS",
                        "Departamento Contabil/Declaracoes RFB/ECD/2026/1.Recibos",
                        "Departamento Contabil/Declaracoes RFB/ECF/2025/1.Recibos",
                        "Departamento Fiscal/Protocolos/Declaracao Estaduais/DMED/2025",
                        "Departamento Fiscal/Protocolos/Declaracao Federais/IRPF/2026/02.2026/ANEXOS",
                        "Departamento Fiscal/Tributos/EMPRESA A/FILIAL 1/2026/GUIAS/03.2026")),
                relativos(raiz, alvos));

        assertEquals(new TreeSet<>(Set.of(5, 7)), profundidades(raiz, alvos));
    }

    @Test
    void umaLinhaExtraAcrescentaUmaProfundidadeNovaSemMexerNasOutras(@TempDir Path raiz) throws IOException {
        montarArvoreDoClienteReal(raiz);
        criar(raiz, "Departamento Pessoal/FOLHA/2026/GUIAS");

        List<Path> alvos = new MapaScanner().escanear(raiz,
                mapa(MAPA_CLIENTE_REAL + "Departamento Pessoal/Folha/$ANO/GUIAS\n"),
                ANOS_2025_2026, RESERVADOS);

        assertTrue(relativos(raiz, alvos).contains("Departamento Pessoal/FOLHA/2026/GUIAS"));
        assertEquals(new TreeSet<>(Set.of(4, 5, 7)), profundidades(raiz, alvos));
        assertEquals(7, alvos.size(), "as 6 pastas-alvo anteriores continuam valendo");
    }

    @Test
    void departamentoNaoMencionadoNuncaEVisitado(@TempDir Path raiz) throws IOException {
        montarArvoreDoClienteReal(raiz);
        ObservadorGravador observador = new ObservadorGravador();

        new MapaScanner((m, e) -> { }, observador)
                .escanear(raiz, mapa(MAPA_CLIENTE_REAL), ANOS_2025_2026, RESERVADOS);

        assertFalse(observador.visitadas.contains("Departamento Pessoal"));
        assertTrue(observador.podadas.contains("Departamento Pessoal"));
        assertTrue(observador.podadas.contains("EMPRESA INATIVO LTDA"));
        assertFalse(observador.visitadas.contains("FOLHA"));
    }

    @Test
    void motivoDaPodaCitaOQueEraEsperadoEAJanelaDeAnos(@TempDir Path raiz) throws IOException {
        criar(raiz, "Departamento Contabil/Declaracoes RFB/ECD/2019");
        ObservadorGravador observador = new ObservadorGravador();

        new MapaScanner((m, e) -> { }, observador)
                .escanear(raiz, mapa(MAPA_CLIENTE_REAL), ANOS_2025_2026, RESERVADOS);

        String motivoDo2019 = observador.motivos.stream()
                .filter(m -> m.contains("\"2019\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("esperava poda de 2019: " + observador.motivos));

        assertTrue(motivoDo2019.contains("$ANO"), motivoDo2019);
        assertTrue(motivoDo2019.contains("2025..2026"), motivoDo2019);
    }

    /**
     * Sem a regra de nomes reservados o mapa se autoenvenena: o {@code *} do template do DCTFWEB
     * casaria a pasta {@code ENVIADOS} criada pelo proprio robo, e no ciclo seguinte ele passaria a
     * criar {@code ENVIADOS/ENVIADOS/...} e a renomear arquivos ja enviados.
     */
    @Test
    void pastaDeTrabalhoDoRoboNuncaEAlvoAtravesDeCuringa(@TempDir Path raiz) throws IOException {
        montarArvoreDoClienteReal(raiz);

        Set<String> alvos = relativos(raiz, new MapaScanner()
                .escanear(raiz, mapa(MAPA_CLIENTE_REAL), ANOS_2025_2026, RESERVADOS));

        assertTrue(alvos.stream().noneMatch(a -> a.contains("ENVIADOS")), alvos.toString());
    }

    @Test
    void pastaDeTrabalhoDoRoboEAlvoQuandoNomeadaPorExtenso(@TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/ENVIADOS", "Cliente A/OUTRA");

        Set<String> alvos = relativos(raiz, new MapaScanner()
                .escanear(raiz, mapa("*/ENVIADOS\n"), null, RESERVADOS));

        assertEquals(Set.of("Cliente A/ENVIADOS"), alvos);
    }

    @Test
    void pastaEnviarConfiguradaTambemEProtegida(@TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/ENVIAR", "Cliente A/2026");
        Set<String> reservadosComEnviar = new HashSet<>(RESERVADOS);
        reservadosComEnviar.add(CustomizacaoRule.normalizar("ENVIAR"));

        Set<String> alvos = relativos(raiz, new MapaScanner()
                .escanear(raiz, mapa("Cliente A/*\n"), null, reservadosComEnviar));

        assertEquals(Set.of("Cliente A/2026"), alvos);
    }

    /**
     * Linhas do mapa sao caminhos independentes e se somam (uniao), entao negativas em linhas
     * SEPARADAS nao restringem nada: cada pasta excluida por uma linha e aceita pela outra.
     *
     * <p>Este e o erro mais facil de cometer no mapa, e vem de transferir a intuicao da
     * {@code CUSTOMIZACAO}, onde duas regras {@code >} do mesmo nivel se combinam como E. O
     * comportamento aqui e intencional (uniao e o que garante monotonicidade), e o readme secao
     * 6.6 avisa sobre ele - este teste existe para o aviso nao poder divergir do codigo.
     */
    @Test
    void duasLinhasComNegativasOpostasAnulamAExclusao(@TempDir Path raiz) throws IOException {
        criar(raiz, "IRPF/2026", "DMED/2026", "TESTE/2026");

        Set<String> comLinhasSeparadas = relativos(raiz, new MapaScanner()
                .escanear(raiz, mapa("!DMED/$ANO\n!TESTE/$ANO\n"), ANOS_2025_2026, RESERVADOS));

        assertEquals(new TreeSet<>(Set.of("DMED/2026", "IRPF/2026", "TESTE/2026")), comLinhasSeparadas,
                "duas linhas se somam: nada e efetivamente excluido");

        Set<String> comUmSoSegmento = relativos(raiz, new MapaScanner()
                .escanear(raiz, mapa("!DMED@!TESTE/$ANO\n"), ANOS_2025_2026, RESERVADOS));

        assertEquals(Set.of("IRPF/2026"), comUmSoSegmento,
                "no mesmo segmento as negativas se somam como E");
    }

    @Test
    void doisTemplatesNaMesmaPastaGeramUmaSoEntrada(@TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026");

        List<Path> alvos = new MapaScanner()
                .escanear(raiz, mapa("Cliente A/$ANO\nCliente A/*\n"), ANOS_2025_2026, RESERVADOS);

        assertEquals(1, alvos.size());
    }

    @Test
    void alvosAninhadosSaoAmbosDevolvidos(@TempDir Path raiz) throws IOException {
        criar(raiz, "A/B/C");

        Set<String> alvos = relativos(raiz, new MapaScanner()
                .escanear(raiz, mapa("A/B\nA/B/C\n"), null, RESERVADOS));

        assertEquals(new TreeSet<>(Set.of("A/B", "A/B/C")), alvos);
    }

    @Test
    void naViradaDoAnoOsRamosDoAnoMaisAntigoSaemDaLista(@TempDir Path raiz) throws IOException {
        montarArvoreDoClienteReal(raiz);

        Set<String> em2027 = relativos(raiz, new MapaScanner()
                .escanear(raiz, mapa(MAPA_CLIENTE_REAL), JanelaAnos.criar(2027), RESERVADOS));

        assertFalse(em2027.contains("Departamento Fiscal/Protocolos/Declaracao Estaduais/DMED/2025"));
        assertFalse(em2027.contains("Departamento Contabil/Declaracoes RFB/ECF/2025/1.Recibos"));
        assertTrue(em2027.contains("Departamento Contabil/Declaracoes RFB/ECD/2026/1.Recibos"));
    }

    @Test
    void raizInexistenteDevolveListaVaziaComUmAvisoESemExcecao(@TempDir Path pasta) {
        List<String> avisos = new ArrayList<>();

        List<Path> alvos = new MapaScanner((m, e) -> avisos.add(m), ObservadorVarredura.NENHUM)
                .escanear(pasta.resolve("nao-existe"), mapa(MAPA_CLIENTE_REAL), ANOS_2025_2026, RESERVADOS);

        assertTrue(alvos.isEmpty());
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).contains("Falha ao listar subpastas"));
    }

    /**
     * Tripwire de custo de I/O: com poucos templates vivos, a varredura nao pode enumerar a arvore
     * inteira. Num compartilhamento de rede cada pasta listada custa milissegundos.
     */
    @Test
    void naoEnumeraAArvoreInteiraQuandoOsTemplatesPodamCedo(@TempDir Path raiz) throws IOException {
        for (int a = 0; a < 30; a++) {
            for (int b = 0; b < 30; b++) {
                criar(raiz, "Cliente " + a + "/Ano " + b + "/Mes");
            }
        }
        ObservadorGravador observador = new ObservadorGravador();

        new MapaScanner((m, e) -> { }, observador)
                .escanear(raiz, mapa("Cliente 7/Ano 3/Mes\n"), null, RESERVADOS);

        assertTrue(observador.visitadas.size() <= 4,
                "visitou " + observador.visitadas.size() + " pastas: " + observador.visitadas);
    }
}
