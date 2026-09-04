package br.com.ottimizza.robo.diagnostico;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestarPastasTest {

    private record Execucao(int codigo, String saida) {
    }

    private static Execucao executar(Path pastaInstalacao, boolean verboso) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int codigo;
        try (PrintStream saida = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            codigo = TestarPastas.executar(pastaInstalacao, saida, verboso);
        }
        return new Execucao(codigo, buffer.toString(StandardCharsets.UTF_8));
    }

    private static void escreverParametros(Path pastaInstalacao, Path raiz, int niveis) throws IOException {
        Files.writeString(pastaInstalacao.resolve("parametros.txt"), """
                CONTABILIDADE=Escritorio
                PASTA_INICIAL=%s
                NIVEIS_SUBPASTA=%d
                """.formatted(raiz.toString().replace('\\', '/'), niveis), StandardCharsets.UTF_8);
    }

    private static void escreverMapa(Path pastaInstalacao, String conteudo) throws IOException {
        Files.writeString(pastaInstalacao.resolve("mapa-pastas.txt"), conteudo, StandardCharsets.UTF_8);
    }

    private static void criar(Path raiz, String... caminhos) throws IOException {
        for (String caminho : caminhos) {
            Files.createDirectories(raiz.resolve(caminho.replace('/', java.io.File.separatorChar)));
        }
    }

    private static Set<String> listagemRecursiva(Path pasta) throws IOException {
        try (Stream<Path> stream = Files.walk(pasta)) {
            return stream.map(p -> pasta.relativize(p).toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    // -------------------------------------------------------------------------------- sucesso

    @Test
    void listaAsPastasAlvoDoMapaEDevolveZero(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026/GUIAS", "Cliente B/2026/GUIAS", "Cliente A/2019/GUIAS");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "*/$ANO/GUIAS\n");

        Execucao execucao = executar(instalacao, false);

        assertEquals(TestarPastas.OK, execucao.codigo(), execucao.saida());
        assertTrue(execucao.saida().contains("PASTAS-ALVO (2)"), execucao.saida());
        assertTrue(execucao.saida().contains(raiz.resolve("Cliente A").resolve("2026").resolve("GUIAS").toString()));
        assertTrue(execucao.saida().contains("MODO TESTE"), execucao.saida());
    }

    @Test
    void ecoaOsTemplatesInterpretadosEAJanelaDeAnos(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/03.2026");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "*/$MES.ANO\n");

        String saida = executar(instalacao, false).saida();

        assertTrue(saida.contains("$MES.ANO"), saida);
        assertTrue(saida.contains("nivel-alvo 2"), saida);
        assertTrue(saida.contains("Anos aceitos"), saida);
        assertTrue(saida.contains("2025..2026"), saida);
    }

    @Test
    void explicaOMotivoDeCadaPoda(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026", "Cliente A/2019");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "*/$ANO\n");

        String saida = executar(instalacao, false).saida();

        assertTrue(saida.contains("podado: 2019"), saida);
        assertTrue(saida.contains("$ANO"), saida);
    }

    @Test
    void avisaQuandoOMapaIgnoraChavesPreenchidas(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/2026");
        escreverParametros(instalacao, raiz, 5);
        escreverMapa(instalacao, "*/$ANO\n");

        String saida = executar(instalacao, false).saida();

        assertTrue(saida.contains("Ignorado por causa do mapa"), saida);
        assertTrue(saida.contains("NIVEIS_SUBPASTA=5"), saida);
    }

    @Test
    void semMapaMostraOModoNiveisSubpasta(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026");
        escreverParametros(instalacao, raiz, 2);

        String saida = executar(instalacao, false).saida();

        assertTrue(saida.contains("Modo           = NIVEIS"), saida);
        assertTrue(saida.contains("Nao existe mapa-pastas.txt"), saida);
    }

    // ------------------------------------------------------------------------------- problemas

    @Test
    void configuracaoInvalidaDizQualChaveEOProblema(@TempDir Path instalacao) throws IOException {
        Files.writeString(instalacao.resolve("parametros.txt"), """
                CONTABILIDADE=Escritorio
                PASTA_INICIAL=Tareffa
                NIVEIS_SUBPASTA=2
                """, StandardCharsets.UTF_8);

        Execucao execucao = executar(instalacao, false);

        assertEquals(TestarPastas.CONFIGURACAO_INVALIDA, execucao.codigo());
        assertTrue(execucao.saida().contains("PASTA_INICIAL"), execucao.saida());
        assertTrue(execucao.saida().contains("caminho absoluto"), execucao.saida());
    }

    @Test
    void parametrosAusenteEDiagnosticado(@TempDir Path instalacao) {
        Execucao execucao = executar(instalacao, false);

        assertEquals(TestarPastas.CONFIGURACAO_INVALIDA, execucao.codigo());
        assertTrue(execucao.saida().contains("nao existe"), execucao.saida());
    }

    @Test
    void mapaInvalidoCitaOArquivoEALinha(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "Cliente A/$ANO\nCliente B/$AN0\n");

        Execucao execucao = executar(instalacao, false);

        assertEquals(TestarPastas.CONFIGURACAO_INVALIDA, execucao.codigo());
        assertTrue(execucao.saida().contains("mapa-pastas.txt linha 2"), execucao.saida());
        assertTrue(execucao.saida().contains("$AN0"), execucao.saida());
    }

    @Test
    void nenhumaPastaAlvoTemCodigoProprioEExplicacao(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/2019");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "*/$ANO\n");

        Execucao execucao = executar(instalacao, false);

        assertEquals(TestarPastas.NENHUMA_PASTA_ALVO, execucao.codigo());
        assertTrue(execucao.saida().contains("nenhuma pasta-alvo"), execucao.saida());
        assertTrue(execucao.saida().contains("nao enviaria nenhum arquivo"), execucao.saida());
    }

    @Test
    void pastaInicialInexistenteEDiagnosticadaAntesDaVarredura(@TempDir Path instalacao, @TempDir Path base)
            throws IOException {
        escreverParametros(instalacao, base.resolve("nao-existe"), 2);

        Execucao execucao = executar(instalacao, false);

        assertEquals(TestarPastas.CONFIGURACAO_INVALIDA, execucao.codigo());
        assertTrue(execucao.saida().contains("NAO ENCONTRADA"), execucao.saida());
    }

    /**
     * O diagnostico que faltava no Robo 1.0: um ramo aceito pelo mapa que nao chega a nenhuma
     * pasta-alvo porque a arvore do cliente tem um nivel a menos ali.
     */
    @Test
    void reportaRamoAceitoQueNaoChegaAPastaAlvo(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "ECD/2026/1.Recibos", "IBGE/2026");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "ECD@IBGE/$ANO/1.Recibos\n");

        Execucao execucao = executar(instalacao, false);

        assertEquals(TestarPastas.OK, execucao.codigo(), execucao.saida());
        assertTrue(execucao.saida().contains("RAMOS SEM PASTA-ALVO (1)"), execucao.saida());
        assertTrue(execucao.saida().contains("IBGE"), execucao.saida());
        assertTrue(execucao.saida().contains("1.Recibos no nivel 3"), execucao.saida());
    }

    /** Reportar pai e filho do mesmo ramo seria ruido: so o mais profundo interessa. */
    @Test
    void ramoSemAlvoLimitaSeAoNivelMaisProfundo(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/2026");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "*/$ANO/GUIAS/RECIBOS\n");

        String saida = executar(instalacao, false).saida();

        assertTrue(saida.contains("RAMOS SEM PASTA-ALVO (1)"), saida);
        assertTrue(saida.contains(raiz.resolve("Cliente A").resolve("2026").toString()), saida);
        assertTrue(saida.contains("ramos sem alvo: 1"), "o resumo nao pode contradizer a lista: " + saida);
    }

    @Test
    void semRamoSemAlvoASecaoNaoAparece(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "*/$ANO\n");

        assertTrue(!executar(instalacao, false).saida().contains("RAMOS SEM PASTA-ALVO"));
    }

    @Test
    void avisaSobrePastaDeTrabalhoDoRoboEntreAsPastasAlvo(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A/ENVIADOS");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "Cliente A/ENVIADOS\n");

        String saida = executar(instalacao, false).saida();

        assertTrue(saida.contains("pasta de trabalho do proprio robo"), saida);
    }

    @Test
    void avisaQuandoUmaPastaAlvoContemOutra(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "Cliente A\nCliente A/$ANO\n");

        String saida = executar(instalacao, false).saida();

        assertTrue(saida.contains("contem outra pasta-alvo"), saida);
    }

    // ------------------------------------------------------------------------------ invariantes

    /**
     * A invariante que torna o comando seguro de rodar na maquina do cliente: nada e escrito, nem
     * na arvore varrida (onde mora o cache) nem na pasta de instalacao (onde moram os logs).
     */
    @Test
    void naoEscreveNadaEmDisco(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026/GUIAS", "Cliente A/2019/GUIAS");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "*/$ANO/GUIAS\n");
        Set<String> antesRaiz = listagemRecursiva(raiz);
        Set<String> antesInstalacao = listagemRecursiva(instalacao);

        executar(instalacao, true);

        assertEquals(antesRaiz, listagemRecursiva(raiz));
        assertEquals(antesInstalacao, listagemRecursiva(instalacao));
    }

    /** O console do Windows usa a codepage da maquina; acento sairia corrompido no relatorio. */
    @Test
    void aSaidaEApenasAscii(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Departamento Fiscal/2026");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "Departamento Fiscal/$ANO\n");

        String saida = executar(instalacao, true).saida();

        String naoAscii = saida.chars()
                .filter(c -> c > 127)
                .mapToObj(c -> "U+" + Integer.toHexString(c))
                .distinct()
                .collect(Collectors.joining(", "));
        assertEquals("", naoAscii, "caracteres nao-ASCII na saida: " + naoAscii);
    }

    @Test
    void verboseMostraAsPastasVisitadas(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        criar(raiz, "Cliente A/2026/GUIAS");
        escreverParametros(instalacao, raiz, 2);
        escreverMapa(instalacao, "*/$ANO/GUIAS\n");

        assertTrue(executar(instalacao, true).saida().contains("entrou: Cliente A"));
        assertTrue(!executar(instalacao, false).saida().contains("entrou: Cliente A"));
    }
}
