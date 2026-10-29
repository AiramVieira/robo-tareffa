package br.com.ottimizza.robo.mapeador;

import br.com.ottimizza.robo.config.Parametros;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A arvore e a do cliente real que sai do Robo 1.0 (a mesma do {@code MapaScannerTest}), com duas
 * empresas a mais: uma que chama a pasta de guias de outro jeito e uma que ainda nao tem a pasta.
 */
class MapeadorApiTest {

    private static final String MAPA_CLIENTE_REAL = """
            Departamento Fiscal/Tributos/*/*/$ANO/GUIAS/$MES.ANO
            Departamento Fiscal/Protocolos/Declaracao Estaduais/!DMED/$ANO/$MES.ANO/*
            Departamento Fiscal/Protocolos/Declaracao Estaduais/DMED/$ANO
            Departamento Fiscal/Protocolos/Declaracao Federais/!DMED/$ANO/$MES.ANO/*
            Departamento Fiscal/Protocolos/Declaracao Federais/DMED/$ANO
            Departamento Contabil/Declaracoes RFB/DCTFWEB - MIT/$MES.ANO/*
            Departamento Contabil/Declaracoes RFB/ECD@ECF@IBGE@SIMPLES/$ANO/1.Recibos
            """;

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-28T15:30:00Z"), ZoneOffset.UTC);

    // ---------------------------------------------------------------- infraestrutura do fixture

    private static void criar(Path raiz, String... caminhos) throws IOException {
        for (String caminho : caminhos) {
            Files.createDirectories(raiz.resolve(caminho.replace('/', java.io.File.separatorChar)));
        }
    }

    private static void montarArvore(Path raiz) throws IOException {
        criar(raiz,
                "Departamento Fiscal/Tributos/EMPRESA A/FILIAL 1/2026/GUIAS/03.2026",
                "Departamento Fiscal/Tributos/EMPRESA A/FILIAL 1/2026/GUIAS/03.2019",
                "Departamento Fiscal/Tributos/EMPRESA A/FILIAL 1/2026/EXTRATOS",
                "Departamento Fiscal/Tributos/EMPRESA B/MATRIZ/2026/Guias Pagas/04.2026",
                "Departamento Fiscal/Tributos/EMPRESA C/MATRIZ/2026/DOCUMENTOS",
                "Departamento Fiscal/Protocolos/Declaracao Federais/IRPF/2026/02.2026/ANEXOS",
                "Departamento Fiscal/Protocolos/Declaracao Estaduais/DMED/2025",
                "Departamento Fiscal/Protocolos/Declaracao Estaduais/DMED/2019",
                "Departamento Contabil/Declaracoes RFB/DCTFWEB - MIT/01.2026/RECIBOS",
                "Departamento Contabil/Declaracoes RFB/DCTFWEB - MIT/01.2026/ENVIADOS/2026/09",
                "Departamento Contabil/Declaracoes RFB/ECD/2026/1.Recibos",
                "Departamento Contabil/Declaracoes RFB/ECF/2025/1.Recibos");
    }

    private static Parametros parametros(Path raiz) {
        return new Parametros("Escritorio", raiz.toString(), 2, 30, 1, "", false, "", "");
    }

    private static MapeadorApi api(Path instalacao, Path raiz) {
        return new MapeadorApi(instalacao, parametros(raiz), 2026, RELOGIO);
    }

    private static Set<String> caminhos(List<MapeadorApi.Alvo> alvos) {
        return alvos.stream().map(MapeadorApi.Alvo::caminho).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> listagemRecursiva(Path pasta) throws IOException {
        try (Stream<Path> stream = Files.walk(pasta)) {
            return stream.map(p -> pasta.relativize(p).toString()).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    // ---------------------------------------------------------------------------- explorador

    @Test
    void subpastasVemEmOrdemEMarcamAsPastasDeTrabalhoDoRobo(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        montarArvore(raiz);

        MapeadorApi.Subpastas subpastas = api(instalacao, raiz)
                .subpastas("Departamento Contabil/Declaracoes RFB/DCTFWEB - MIT/01.2026");

        assertEquals(List.of(new MapeadorApi.Subpasta("ENVIADOS", true), new MapeadorApi.Subpasta("RECIBOS", false)),
                subpastas.pastas());
        assertFalse(subpastas.semAcesso());
    }

    @Test
    void subpastasRecusaCaminhoQueSaiDaArvoreDoCliente(@TempDir Path instalacao, @TempDir Path raiz) {
        MapeadorApi api = api(instalacao, raiz);

        assertThrows(IllegalArgumentException.class, () -> api.subpastas(".."));
        assertThrows(IllegalArgumentException.class, () -> api.subpastas("Departamento Fiscal/../.."));
        assertThrows(IllegalArgumentException.class, () -> api.subpastas("C:/Windows"));
        assertThrows(IllegalArgumentException.class, () -> api.subpastas("Departamento Fiscal\\..\\.."));
    }

    @Test
    void pastaQueNaoAbreViraSemAcessoEmVezDeErro(@TempDir Path instalacao, @TempDir Path raiz) {
        MapeadorApi.Subpastas subpastas = api(instalacao, raiz).subpastas("nao existe");

        assertTrue(subpastas.semAcesso());
        assertTrue(subpastas.pastas().isEmpty());
    }

    @Test
    void pastaCriadaDuranteASessaoSoApareceDepoisDeAtualizar(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        criar(raiz, "Cliente A");
        MapeadorApi api = api(instalacao, raiz);
        assertEquals(1, api.subpastas("").pastas().size());

        criar(raiz, "Cliente B");
        assertEquals(1, api.subpastas("").pastas().size(), "a listagem fica memorizada durante a sessao");

        api.esquecerPastas();
        assertEquals(2, api.subpastas("").pastas().size());
    }

    // ------------------------------------------------------------------------ nomes por nivel

    @Test
    void nomesDoNivelSomamTodosOsRamosAceitosNoNivelDeCima(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        montarArvore(raiz);

        MapeadorApi.NomesNoNivel nomes = api(instalacao, raiz).nomesNoNivel(
                List.of("Departamento Fiscal", "Tributos", "*", "*", "$ANO"), "GUIAS");

        assertNull(nomes.erro());
        assertEquals(3, nomes.pastasNoNivelAcima(), "uma pasta de ano por empresa");
        assertEquals(List.of(
                        new MapeadorApi.NomeNoNivel("DOCUMENTOS", 1, false, false),
                        new MapeadorApi.NomeNoNivel("EXTRATOS", 1, false, false),
                        new MapeadorApi.NomeNoNivel("GUIAS", 1, true, false),
                        new MapeadorApi.NomeNoNivel("Guias Pagas", 1, true, false)),
                nomes.nomes(), "GUIAS casa por pedaco do nome, entao alcanca tambem Guias Pagas");
    }

    @Test
    void nomesDoPrimeiroNivelSaoAsPastasDaRaiz(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        montarArvore(raiz);

        MapeadorApi.NomesNoNivel nomes = api(instalacao, raiz).nomesNoNivel(List.of(), "!Contabil");

        assertEquals(1, nomes.pastasNoNivelAcima());
        assertEquals(List.of(
                        new MapeadorApi.NomeNoNivel("Departamento Contabil", 1, false, false),
                        new MapeadorApi.NomeNoNivel("Departamento Fiscal", 1, true, false)),
                nomes.nomes());
    }

    @Test
    void pastaDeTrabalhoDoRoboSoEntraQuandoNomeadaPorExtenso(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        montarArvore(raiz);
        MapeadorApi api = api(instalacao, raiz);
        List<String> prefixo = List.of("Departamento Contabil", "Declaracoes RFB", "DCTFWEB - MIT", "$MES.ANO");

        MapeadorApi.NomeNoNivel porCuringa = api.nomesNoNivel(prefixo, "*").nomes().get(0);
        MapeadorApi.NomeNoNivel porExtenso = api.nomesNoNivel(prefixo, "ENVIADOS").nomes().get(0);

        assertEquals("ENVIADOS", porCuringa.nome());
        assertTrue(porCuringa.doRobo());
        assertFalse(porCuringa.aceita());
        assertTrue(porExtenso.aceita());
    }

    @Test
    void regraInvalidaNoNivelVoltaComoErroSemDerrubarALista(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        montarArvore(raiz);

        MapeadorApi.NomesNoNivel nomes = api(instalacao, raiz).nomesNoNivel(List.of(), "$AN0");

        assertNotNull(nomes.erro());
        assertTrue(nomes.erro().contains("$AN0"), nomes.erro());
        assertEquals(2, nomes.nomes().size());
        assertTrue(nomes.nomes().stream().noneMatch(MapeadorApi.NomeNoNivel::aceita));
    }

    @Test
    void regraDoNivelDeCimaComBarraERecusada(@TempDir Path instalacao, @TempDir Path raiz) {
        MapeadorApi.NomesNoNivel nomes = api(instalacao, raiz).nomesNoNivel(List.of("A/B"), "*");

        assertNotNull(nomes.erro());
        assertTrue(nomes.nomes().isEmpty());
    }

    // ---------------------------------------------------------------------------------- previa

    @Test
    void previaUsaOMotorDoRoboEContaAsPastasDeCadaLinha(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        montarArvore(raiz);

        MapeadorApi.Previa previa = api(instalacao, raiz).previa(MAPA_CLIENTE_REAL);

        assertEquals("ok", previa.situacao());
        assertEquals(new TreeSet<>(Set.of(
                        "Departamento Contabil/Declaracoes RFB/DCTFWEB - MIT/01.2026/RECIBOS",
                        "Departamento Contabil/Declaracoes RFB/ECD/2026/1.Recibos",
                        "Departamento Contabil/Declaracoes RFB/ECF/2025/1.Recibos",
                        "Departamento Fiscal/Protocolos/Declaracao Estaduais/DMED/2025",
                        "Departamento Fiscal/Protocolos/Declaracao Federais/IRPF/2026/02.2026/ANEXOS",
                        "Departamento Fiscal/Tributos/EMPRESA A/FILIAL 1/2026/GUIAS/03.2026",
                        "Departamento Fiscal/Tributos/EMPRESA B/MATRIZ/2026/Guias Pagas/04.2026")),
                caminhos(previa.alvos()));
        assertEquals(7, previa.totalDeAlvos());
        assertEquals(Map.of(1, 2, 3, 1, 4, 1, 6, 1, 7, 2), previa.alvosPorLinha());
    }

    @Test
    void ramoSemAlvoDizOQueOCaminhoEsperavaEncontrar(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        montarArvore(raiz);

        MapeadorApi.Previa previa = api(instalacao, raiz).previa(MAPA_CLIENTE_REAL);

        assertEquals(List.of(new MapeadorApi.RamoSemAlvo(
                        "Departamento Fiscal/Tributos/EMPRESA C/MATRIZ/2026", List.of("GUIAS"))),
                previa.ramosSemAlvo(), "so o ramo mais fundo; MATRIZ e EMPRESA C ficaram sem alvo por causa dele");
    }

    @Test
    void pastaAlcancadaPorDuasLinhasContaParaAsDuas(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        montarArvore(raiz);

        MapeadorApi.Previa previa = api(instalacao, raiz).previa("""
                Departamento Contabil/Declaracoes RFB/ECD/$ANO/1.Recibos
                Departamento Contabil/Declaracoes RFB/*/$ANO/1.Recibos
                """);

        assertEquals(2, previa.totalDeAlvos());
        assertEquals(Map.of(1, 1, 2, 2), previa.alvosPorLinha(),
                "a linha 1 nao pode aparecer com zero so porque a 2 tambem chega na mesma pasta");
        MapeadorApi.Alvo ecd = previa.alvos().stream()
                .filter(a -> a.caminho().contains("/ECD/")).findFirst().orElseThrow();
        assertEquals(List.of(1, 2), ecd.linhas());
    }

    @Test
    void previaAvisaPastaMonitoradaDentroDeOutra(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        montarArvore(raiz);

        MapeadorApi.Previa previa = api(instalacao, raiz).previa("""
                Departamento Contabil/Declaracoes RFB/ECD
                Departamento Contabil/Declaracoes RFB/ECD/$ANO
                """);

        assertEquals(List.of(new MapeadorApi.AlvoAninhado(
                        "Departamento Contabil/Declaracoes RFB/ECD/2026", "Departamento Contabil/Declaracoes RFB/ECD")),
                previa.alvosAninhados());
    }

    @Test
    void previaDeMapaInvalidoApontaALinha(@TempDir Path instalacao, @TempDir Path raiz) {
        MapeadorApi.Previa previa = api(instalacao, raiz).previa("""
                # comentario
                Departamento Fiscal/*
                Departamento Fiscal/$AN0
                """);

        assertEquals("invalida", previa.situacao());
        assertEquals(3, previa.linhaDoErro());
        assertTrue(previa.erro().contains("$AN0"), previa.erro());
    }

    @Test
    void nadaEEscritoNaArvoreDoCliente(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        montarArvore(raiz);
        Set<String> antes = listagemRecursiva(raiz);
        MapeadorApi api = api(instalacao, raiz);

        api.subpastas("Departamento Fiscal");
        api.nomesNoNivel(List.of("Departamento Fiscal", "*"), "*");
        api.previa(MAPA_CLIENTE_REAL);

        assertEquals(antes, listagemRecursiva(raiz));
    }

    // --------------------------------------------------------------------------------- salvar

    @Test
    void salvarGravaOMapaEmUtf8EGuardaOAnterior(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        Path arquivo = instalacao.resolve("mapa-pastas.txt");
        Files.writeString(arquivo, "Antigo/*\n", StandardCharsets.UTF_8);

        MapeadorApi.Salvamento salvamento = api(instalacao, raiz).salvar("Declaração Estaduais/$ANO\r\n");

        assertTrue(salvamento.salvo(), salvamento.erro());
        assertEquals("Declaração Estaduais/$ANO\r\n", Files.readString(arquivo, StandardCharsets.UTF_8));
        Path copia = instalacao.resolve("mapa-pastas-anteriores").resolve("mapa-pastas_2026-10-28_153000.txt");
        assertEquals("Antigo/*\n", Files.readString(copia, StandardCharsets.UTF_8));
        assertEquals(instalacao.relativize(copia).toString(), salvamento.copiaAnterior());
        try (Stream<Path> sobras = Files.list(instalacao)) {
            assertTrue(sobras.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void salvarSemMapaAnteriorNaoCriaCopia(@TempDir Path instalacao, @TempDir Path raiz) {
        MapeadorApi.Salvamento salvamento = api(instalacao, raiz).salvar("Clientes/*\n");

        assertTrue(salvamento.salvo());
        assertNull(salvamento.copiaAnterior());
        assertFalse(Files.exists(instalacao.resolve("mapa-pastas-anteriores")));
    }

    @Test
    void salvarOMesmoConteudoNaoGuardaCopiaRepetida(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        Files.writeString(instalacao.resolve("mapa-pastas.txt"), "Clientes/*\n", StandardCharsets.UTF_8);

        MapeadorApi.Salvamento salvamento = api(instalacao, raiz).salvar("Clientes/*\n");

        assertTrue(salvamento.salvo());
        assertNull(salvamento.copiaAnterior());
    }

    @Test
    void salvarRecusaMapaInvalidoSemTocarNoArquivo(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        Path arquivo = instalacao.resolve("mapa-pastas.txt");
        Files.writeString(arquivo, "Clientes/*\n", StandardCharsets.UTF_8);

        MapeadorApi.Salvamento salvamento = api(instalacao, raiz).salvar("Clientes/$AN0\n");

        assertFalse(salvamento.salvo());
        assertTrue(salvamento.erro().contains("linha 1"), salvamento.erro());
        assertEquals("Clientes/*\n", Files.readString(arquivo, StandardCharsets.UTF_8));
        assertFalse(Files.exists(instalacao.resolve("mapa-pastas-anteriores")));
    }

    @Test
    void salvarRecusaMapaSemNenhumCaminho(@TempDir Path instalacao, @TempDir Path raiz) {
        MapeadorApi.Salvamento salvamento = api(instalacao, raiz).salvar("# so comentario\n");

        assertFalse(salvamento.salvo());
        assertFalse(Files.exists(instalacao.resolve("mapa-pastas.txt")));
    }

    // --------------------------------------------------------------------------------- estado

    @Test
    void estadoSemMapaIndicaOModoNiveis(@TempDir Path instalacao, @TempDir Path raiz) {
        MapeadorApi.Estado estado = api(instalacao, raiz).estado();

        assertEquals("NIVEIS", estado.modoAtual());
        assertNull(estado.textoMapa());
        assertNull(estado.erroMapa());
        assertEquals(2025, estado.anoMinimo());
        assertEquals(2026, estado.anoMaximo());
        assertEquals(List.of("ENVIADOS", "ERROS"), estado.pastasDoRobo());
    }

    @Test
    void estadoTrazOMapaAtualEOErroDele(@TempDir Path instalacao, @TempDir Path raiz) throws IOException {
        Files.writeString(instalacao.resolve("mapa-pastas.txt"), "Clientes|Fornecedores/*\n", StandardCharsets.UTF_8);

        MapeadorApi.Estado estado = api(instalacao, raiz).estado();

        assertEquals("MAPA", estado.modoAtual());
        assertEquals("Clientes|Fornecedores/*\n", estado.textoMapa());
        assertNotNull(estado.erroMapa());
        assertTrue(estado.erroMapa().contains("@"), estado.erroMapa());
    }

    @Test
    void estadoLeMapaSalvoEmAnsiEExplicaPorQueORoboNaoLe(@TempDir Path instalacao, @TempDir Path raiz)
            throws IOException {
        Files.write(instalacao.resolve("mapa-pastas.txt"),
                "Declaração Estaduais/*\r\n".getBytes(StandardCharsets.ISO_8859_1));

        MapeadorApi.Estado estado = api(instalacao, raiz).estado();

        assertEquals("Declaração Estaduais/*\r\n", estado.textoMapa());
        assertTrue(estado.erroMapa().contains("UTF-8"), estado.erroMapa());
    }
}
