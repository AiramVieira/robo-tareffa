package br.com.ottimizza.robo.discovery;

import br.com.ottimizza.robo.config.Parametros;
import br.com.ottimizza.robo.logging.ErroLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PastaAlvoResolverTest {

    private static final String CACHE = ".robo_pastas_alvo.cache.json";

    private static Parametros parametros(Path raiz, int niveisSubpasta, String customizacao) {
        return new Parametros("Escritorio", raiz.toString(), niveisSubpasta, 30, 24,
                "", false, customizacao, "");
    }

    private static void criar(Path raiz, String... caminhos) throws IOException {
        for (String caminho : caminhos) {
            Files.createDirectories(raiz.resolve(caminho.replace('/', java.io.File.separatorChar)));
        }
    }

    private static void escreverMapa(Path pastaInstalacao, String conteudo) throws IOException {
        Files.writeString(pastaInstalacao.resolve(MapaPastasLoader.NOME_ARQUIVO), conteudo,
                StandardCharsets.UTF_8);
    }

    private static Set<String> relativos(Path raiz, List<Path> pastas) {
        return pastas.stream()
                .map(p -> raiz.relativize(p).toString().replace('\\', '/'))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static List<Path> pastasAlvo(PastaAlvoResolver.Resultado resultado) {
        return assertInstanceOf(PastaAlvoResolver.Resultado.Sucesso.class, resultado).pastasAlvo();
    }

    private static String motivo(PastaAlvoResolver.Resultado resultado) {
        return assertInstanceOf(PastaAlvoResolver.Resultado.ConfiguracaoInvalida.class, resultado).motivo();
    }

    private static PastaAlvoResolver resolver(Path pastaInstalacao) {
        return new PastaAlvoResolver(new ErroLogger(pastaInstalacao), pastaInstalacao);
    }

    // ------------------------------------------------------------------------ modo e precedencia

    /**
     * A arvore e montada de forma que os dois modos dao respostas <b>diferentes</b>: com
     * {@code NIVEIS_SUBPASTA=1} os alvos seriam as duas pastas de nivel 1; com o mapa, apenas o
     * ramo descrito.
     */
    @Test
    void mapaPresenteIgnoraNiveisSubpastaECustomizacao(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Departamento Fiscal/Tributos", "Departamento Pessoal/Folha");
        escreverMapa(instalacao, "Departamento Fiscal/Tributos\n");

        List<Path> alvos = pastasAlvo(resolver(instalacao)
                .resolver(parametros(raiz, 1, "1:>!TRIBUTOS")));

        assertEquals(Set.of("Departamento Fiscal/Tributos"), relativos(raiz, alvos));
    }

    /**
     * Reproduz o exemplo da secao 6.3 do readme atraves do resolver. O termo e o radical
     * {@code INATIV} porque o casamento e por substring: {@code INATIVO} nao alcancaria a pasta
     * {@code EMPRESA INATIVA LTDA} - a armadilha que o proprio exemplo do readme tinha.
     */
    @Test
    void semMapaOComportamentoHistoricoEPreservado(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "EMPRESA ATIVA LTDA/2026", "EMPRESA INATIVA LTDA/2026",
                "EMPRESA CONSOLIDADO LTDA/FILIAL SP/2026");

        List<Path> alvos = pastasAlvo(resolver(instalacao)
                .resolver(parametros(raiz, 2, "1:>!INATIV;1:+CONSOLIDADO")));

        assertEquals(new TreeSet<>(Set.of(
                        "EMPRESA ATIVA LTDA/2026",
                        "EMPRESA CONSOLIDADO LTDA/FILIAL SP/2026")),
                relativos(raiz, alvos));
    }

    /** Ordem facil de errar: a decisao de modo vem antes do curto-circuito de NIVEIS_SUBPASTA=0. */
    @Test
    void mapaGanhaMesmoComNiveisSubpastaZero(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/2026");
        escreverMapa(instalacao, "Cliente A/$ANO\n");

        List<Path> alvos = pastasAlvo(resolver(instalacao).resolver(parametros(raiz, 0, "")));

        assertEquals(Set.of("Cliente A/2026"), relativos(raiz, alvos));
    }

    @Test
    void semMapaNiveisSubpastaZeroContinuaSendoAPropriaRaiz(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/2026");

        List<Path> alvos = pastasAlvo(resolver(instalacao).resolver(parametros(raiz, 0, "")));

        assertEquals(List.of(raiz), alvos);
        assertFalse(Files.exists(raiz.resolve(CACHE)), "modo pasta unica nao precisa de cache");
    }

    @Test
    void descreverModoDizEmQualModoOCicloEsta(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        assertTrue(resolver(instalacao).descreverModo(parametros(raiz, 2, "")).startsWith("NIVEIS"));

        escreverMapa(instalacao, "Cliente A/$ANO\nCliente B/$ANO\n");

        String modo = resolver(instalacao).descreverModo(parametros(raiz, 2, ""));
        assertTrue(modo.startsWith("MAPA"), modo);
        assertTrue(modo.contains("2 templates"), modo);
    }

    // --------------------------------------------------------------------------------- validacao

    @Test
    void mapaInvalidoCitaALinhaENaoTocaNoCache(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/2026");
        PastaAlvoResolver resolver = resolver(instalacao);
        escreverMapa(instalacao, "Cliente A/$ANO\n");
        pastasAlvo(resolver.resolver(parametros(raiz, 2, "")));
        String cacheBom = Files.readString(raiz.resolve(CACHE));

        escreverMapa(instalacao, "Cliente A/$ANO\nCliente B/$AN0\n");
        String motivo = motivo(resolver.resolver(parametros(raiz, 2, "")));

        assertTrue(motivo.contains("linha 2"), motivo);
        assertTrue(motivo.contains("$AN0"), motivo);
        assertEquals(cacheBom, Files.readString(raiz.resolve(CACHE)),
                "o cache anterior nao pode ser sobrescrito por uma configuracao invalida");
    }

    @Test
    void customizacaoMalformadaContinuaSendoConfiguracaoInvalida(@TempDir Path instalacao, @TempDir Path raiz) {
        assertTrue(motivo(resolver(instalacao).resolver(parametros(raiz, 2, "abc:>X")))
                .contains("CUSTOMIZACAO"));
    }

    /**
     * Antes de a janela de anos virar fixa, {@code SUBNIVEL_ANO} sozinho era configuracao invalida
     * porque faltava a chave que definia a janela. Hoje essa e a unica combinacao possivel - e tem
     * de funcionar, senao toda instalacao existente para no primeiro ciclo apos a atualizacao.
     */
    @Test
    void subnivelAnoSozinhoEValidoEUsaAJanelaFixa(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        int anoAtual = Year.now().getValue();
        criar(raiz, "Cliente A/" + anoAtual, "Cliente A/" + (anoAtual - 1), "Cliente A/" + (anoAtual - 5));
        Parametros parametros = new Parametros("Escritorio", raiz.toString(), 2, 30, 24,
                "", false, "", "2");

        assertEquals(Set.of("Cliente A/" + anoAtual, "Cliente A/" + (anoAtual - 1)),
                relativos(raiz, pastasAlvo(resolver(instalacao).resolver(parametros))));
    }

    // ------------------------------------------------------------------------------------- cache

    @Test
    void cacheValidoComMesmaConfiguracaoDispensaAVarredura(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/2026");
        escreverMapa(instalacao, "Cliente A/$ANO\n");
        PastaAlvoResolver resolver = resolver(instalacao);
        pastasAlvo(resolver.resolver(parametros(raiz, 2, "")));

        // A arvore desaparece: se houvesse varredura, o resultado viria vazio.
        apagarRecursivo(raiz.resolve("Cliente A"));
        List<Path> alvos = pastasAlvo(resolver.resolver(parametros(raiz, 2, "")));

        assertEquals(Set.of("Cliente A/2026"), relativos(raiz, alvos));
    }

    @Test
    void editarOMapaInvalidaOCacheNaHora(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026", "Cliente B/2026");
        escreverMapa(instalacao, "Cliente A/$ANO\n");
        PastaAlvoResolver resolver = resolver(instalacao);
        assertEquals(Set.of("Cliente A/2026"),
                relativos(raiz, pastasAlvo(resolver.resolver(parametros(raiz, 2, "")))));

        escreverMapa(instalacao, "Cliente A/$ANO\nCliente B/$ANO\n");
        List<Path> alvos = pastasAlvo(resolver.resolver(parametros(raiz, 2, "")));

        assertEquals(new TreeSet<>(Set.of("Cliente A/2026", "Cliente B/2026")), relativos(raiz, alvos));
    }

    @Test
    void editarCustomizacaoInvalidaOCacheNaHora(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "EMPRESA ATIVA/2026", "EMPRESA INATIVA/2026");
        PastaAlvoResolver resolver = resolver(instalacao);
        assertEquals(2, pastasAlvo(resolver.resolver(parametros(raiz, 2, ""))).size());

        List<Path> alvos = pastasAlvo(resolver.resolver(parametros(raiz, 2, "1:>!INATIV")));

        assertEquals(Set.of("EMPRESA ATIVA/2026"), relativos(raiz, alvos));
    }

    @Test
    void cacheDeOutroAnoEDescartado(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026");
        Parametros parametros = parametros(raiz, 2, "");
        new PastaAlvoCache(raiz).salvar(List.of(raiz.resolve("Cliente A/1999").toString()), Instant.now(),
                ConfiguracaoFingerprint.calcular(parametros, "", 1999));

        List<Path> alvos = pastasAlvo(resolver(instalacao).resolver(parametros));

        assertEquals(Set.of("Cliente A/2026"), relativos(raiz, alvos));
    }

    @Test
    void mudarSoOIntervaloDeVarreduraNaoForcaRemapeamento(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/2026");
        PastaAlvoResolver resolver = resolver(instalacao);
        pastasAlvo(resolver.resolver(parametros(raiz, 2, "")));

        apagarRecursivo(raiz.resolve("Cliente A"));
        Parametros outroIntervalo = new Parametros("Escritorio", raiz.toString(), 2, 60, 24,
                "", false, "", "");

        assertEquals(Set.of("Cliente A/2026"),
                relativos(raiz, pastasAlvo(resolver.resolver(outroIntervalo))));
    }

    // ------------------------------------------------------------------------ nomes reservados

    @Test
    void pastaDeBackupDoRoboNaoEntraNaListaNoRemapeamentoSeguinte(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/01.2026/RECIBOS", "Cliente A/01.2026/ENVIADOS/2026/09");
        escreverMapa(instalacao, "Cliente A/$MES.ANO/*\n");

        List<Path> alvos = pastasAlvo(resolver(instalacao).resolver(parametros(raiz, 2, "")));

        assertEquals(Set.of("Cliente A/01.2026/RECIBOS"), relativos(raiz, alvos));
    }

    // ------------------------------------------------------------------------------ simular()

    /**
     * O modo de teste tem de ser estritamente somente-leitura: o arquivo de cache mora dentro da
     * {@code PASTA_INICIAL}, isto e, na arvore do cliente.
     */
    @Test
    void simularNaoEscreveNadaEmDisco(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026");
        escreverMapa(instalacao, "Cliente A/$ANO\n");
        Set<String> antesRaiz = listagemRecursiva(raiz);
        Set<String> antesInstalacao = listagemRecursiva(instalacao);

        List<Path> alvos = pastasAlvo(resolver(instalacao)
                .simular(parametros(raiz, 2, ""), ObservadorVarredura.NENHUM, (m, e) -> { }));

        assertEquals(Set.of("Cliente A/2026"), relativos(raiz, alvos));
        assertEquals(antesRaiz, listagemRecursiva(raiz));
        assertEquals(antesInstalacao, listagemRecursiva(instalacao));
    }

    @Test
    void simularNaoLeOCacheEntaoSempreReflete0EstadoAtualDaArvore(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/2026");
        PastaAlvoResolver resolver = resolver(instalacao);
        pastasAlvo(resolver.resolver(parametros(raiz, 2, "")));
        criar(raiz, "Cliente B/2026");

        List<Path> alvos = pastasAlvo(
                resolver.simular(parametros(raiz, 2, ""), ObservadorVarredura.NENHUM, (m, e) -> { }));

        assertEquals(2, alvos.size(), "o cache nao pode esconder a pasta nova do diagnostico");
    }

    @Test
    void simularReportaAsPodasPeloObservador(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026", "Cliente A/2019");
        escreverMapa(instalacao, "Cliente A/$ANO\n");
        List<String> motivos = new ArrayList<>();

        resolver(instalacao).simular(parametros(raiz, 2, ""), new ObservadorVarredura() {
            @Override
            public void pastaPodada(Path pasta, int nivel, String motivo) {
                motivos.add(motivo);
            }
        }, (m, e) -> { });

        assertTrue(motivos.stream().anyMatch(m -> m.contains("2019")), motivos.toString());
    }

    // ------------------------------------------------------------------------------- utilitarios

    private static Set<String> listagemRecursiva(Path pasta) throws IOException {
        try (Stream<Path> stream = Files.walk(pasta)) {
            return stream.map(p -> pasta.relativize(p).toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static void apagarRecursivo(Path pasta) throws IOException {
        try (Stream<Path> stream = Files.walk(pasta)) {
            for (Path caminho : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(caminho);
            }
        }
    }
}
