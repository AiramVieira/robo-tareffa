package br.com.ottimizza.robo.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapaPastasLoaderTest {

    private static final String RAIZ = "W:/Clientes";

    private static MapaPastas carregado(String texto) {
        MapaPastasLoader.Resultado resultado = MapaPastasLoader.interpretar(texto, RAIZ);
        return assertInstanceOf(MapaPastasLoader.Resultado.Carregado.class, resultado).mapa();
    }

    private static String motivo(String texto) {
        MapaPastasLoader.Resultado resultado = MapaPastasLoader.interpretar(texto, RAIZ);
        return assertInstanceOf(MapaPastasLoader.Resultado.Invalido.class, resultado).motivo();
    }

    @Test
    void arquivoAusenteNaoEErroEManteneOModoNiveisSubpasta() {
        Optional<MapaPastasLoader.Resultado> resultado =
                MapaPastasLoader.carregar(Path.of("nao-existe-mapa-pastas.txt"), RAIZ);

        assertTrue(resultado.isEmpty());
    }

    @Test
    void carregaTemplatesIgnorandoComentariosELinhasVazias() {
        MapaPastas mapa = carregado("""
                # comentario
                Departamento Fiscal/Tributos/*/$ANO

                   # outro comentario
                Departamento Contabil/Declaracoes RFB/ECD@ECF/$ANO/1.Recibos
                """);

        assertEquals(2, mapa.getTemplates().size());
        assertEquals(4, mapa.getTemplates().get(0).profundidade());
        assertEquals(5, mapa.getTemplates().get(1).profundidade());
        assertEquals(5, mapa.profundidadeMaxima());
        assertTrue(mapa.usaTokenDeAno());
    }

    @Test
    void numeroDaLinhaEPreservadoMesmoComComentarios() {
        MapaPastas mapa = carregado("""
                # cabecalho

                Primeiro/Nivel

                Segundo/Nivel
                """);

        assertEquals(3, mapa.getTemplates().get(0).linha());
        assertEquals(5, mapa.getTemplates().get(1).linha());
    }

    @Test
    void bomUtf8NaPrimeiraLinhaNaoQuebraOTemplate() {
        MapaPastas mapa = carregado('\uFEFF' + "Departamento Fiscal/Tributos");

        assertEquals(2, mapa.getTemplates().get(0).profundidade());
        assertTrue(mapa.getTemplates().get(0).segmento(0).aceita("Departamento Fiscal", null));
    }

    @Test
    void barraNormalEInvertidaSaoAceitasEPodemSeMisturar() {
        MapaPastas mapa = carregado("Departamento Fiscal\\Tributos/Empresa");

        assertEquals(3, mapa.getTemplates().get(0).profundidade());
    }

    @Test
    void separadorFinalEIgnorado() {
        assertEquals(2, carregado("Departamento Fiscal/Tributos/").getTemplates().get(0).profundidade());
        assertEquals(2, carregado("Departamento Fiscal/Tributos\\").getTemplates().get(0).profundidade());
    }

    @Test
    void duasBarrasSeguidasSaoInvalidas() {
        assertTrue(motivo("Departamento Fiscal//Tributos").contains("segmento vazio"));
    }

    @Test
    void prefixoAbsolutoIgualAPastaInicialEAceitoERemovido() {
        for (String linha : List.of(
                "W:/Clientes/Departamento Fiscal/Tributos",
                "w:\\clientes\\Departamento Fiscal\\Tributos",
                "W:/Clientes/Departamento Fiscal/Tributos/")) {
            MapaPastas mapa = carregado(linha);

            assertEquals(2, mapa.getTemplates().get(0).profundidade(), linha);
            assertTrue(mapa.getTemplates().get(0).segmento(0).aceita("Departamento Fiscal", null), linha);
        }
    }

    @Test
    void prefixoAbsolutoPreservaACaixaDosTermos() {
        MapaPastas mapa = carregado("w:\\clientes\\Departamento Fiscal/Tributos");

        assertEquals("Departamento Fiscal", mapa.getTemplates().get(0).segmento(0).textoOriginal());
    }

    @Test
    void templateQueSoRepeteARaizEInvalidoEOrientaSobreNiveisSubpastaZero() {
        String motivo = motivo("W:/Clientes");

        assertTrue(motivo.contains("PASTA_INICIAL"), motivo);
        assertTrue(motivo.contains("NIVEIS_SUBPASTA=0"), motivo);
    }

    @Test
    void prefixoAbsolutoDivergenteCitaAsDuasRaizes() {
        String motivo = motivo("X:/Outra/Departamento Fiscal/Tributos");

        assertTrue(motivo.contains("X:"), motivo);
        assertTrue(motivo.contains("W:/Clientes"), motivo);
        assertTrue(motivo.contains("linha 1"), motivo);
    }

    @Test
    void prefixoUncEAceitoQuandoBateComAPastaInicial() {
        MapaPastasLoader.Resultado resultado = MapaPastasLoader.interpretar(
                "\\\\servidor\\contabil\\Departamento Fiscal\\Tributos", "\\\\servidor\\contabil");
        MapaPastas mapa = assertInstanceOf(MapaPastasLoader.Resultado.Carregado.class, resultado).mapa();

        assertEquals(2, mapa.getTemplates().get(0).profundidade());
    }

    @Test
    void somenteComentariosEInvalidoComOrientacaoDeComoVoltarAoModoAntigo() {
        String motivo = motivo("""
                # Departamento Fiscal/Tributos
                # Departamento Contabil/Declaracoes RFB
                """);

        assertTrue(motivo.contains("nenhum template"), motivo);
        assertTrue(motivo.contains("NIVEIS_SUBPASTA"), motivo);
    }

    @Test
    void arquivoVazioEInvalido() {
        assertTrue(motivo("").contains("nenhum template"));
        assertTrue(motivo("\n\n   \n").contains("nenhum template"));
    }

    @Test
    void erroDeSegmentoCitaArquivoLinhaEMotivo() {
        String motivo = motivo("""
                Departamento Fiscal/Tributos
                Departamento Contabil/$AN0
                """);

        assertTrue(motivo.contains("mapa-pastas.txt"), motivo);
        assertTrue(motivo.contains("linha 2"), motivo);
        assertTrue(motivo.contains("$AN0"), motivo);
    }

    @Test
    void asteriscoDuploERejeitadoComANumeracaoDaLinha() {
        String motivo = motivo("Departamento Fiscal/**/GUIAS");

        assertTrue(motivo.contains("linha 1"), motivo);
        assertTrue(motivo.contains("nao e suportado"), motivo);
    }

    @Test
    void acentuacaoUtf8EPreservada() {
        MapaPastas mapa = carregado("Departamento Fiscal/Protocolos/Declaração Estaduais");

        assertTrue(mapa.getTemplates().get(0).segmento(2).aceita("DECLARACAO ESTADUAIS", null));
        assertTrue(mapa.getTemplates().get(0).textoOriginal().contains("Declaração"));
    }

    @Test
    void excessoDeTemplatesEInvalido() {
        StringBuilder muitos = new StringBuilder();
        for (int i = 0; i <= MapaPastasLoader.MAXIMO_TEMPLATES; i++) {
            muitos.append("Cliente ").append(i).append("/GUIAS\n");
        }

        assertTrue(motivo(muitos.toString()).contains("mais de " + MapaPastasLoader.MAXIMO_TEMPLATES));
    }

    @Test
    void excessoDeNiveisEInvalido() {
        String profundo = "a/".repeat(MapaPastasLoader.MAXIMO_SEGMENTOS + 1) + "b";

        assertTrue(motivo(profundo).contains("maximo " + MapaPastasLoader.MAXIMO_SEGMENTOS));
    }

    @Test
    void textoBrutoEGuardadoParaOFingerprint() {
        String texto = "# nota\nDepartamento Fiscal/Tributos\n";

        assertEquals(texto, carregado(texto).getTextoBruto());
    }

    @Test
    void mapaSemTokenDeAnoEReconhecidoComoTal() {
        assertFalse(carregado("Departamento Fiscal/Tributos/*").usaTokenDeAno());
    }

    @Test
    void leDoDiscoComAcentuacaoENumeroDeLinha(@TempDir Path pasta) throws IOException {
        Path arquivo = pasta.resolve(MapaPastasLoader.NOME_ARQUIVO);
        Files.writeString(arquivo, """
                # mapa do cliente
                Departamento Fiscal/Protocolos/Declaração Estaduais/!DMED/$ANO
                """, StandardCharsets.UTF_8);

        MapaPastasLoader.Resultado resultado = MapaPastasLoader.carregar(arquivo, RAIZ).orElseThrow();
        MapaPastas mapa = assertInstanceOf(MapaPastasLoader.Resultado.Carregado.class, resultado).mapa();

        assertEquals(1, mapa.getTemplates().size());
        assertEquals(2, mapa.getTemplates().get(0).linha());
        assertEquals(5, mapa.getTemplates().get(0).profundidade());
    }
}
