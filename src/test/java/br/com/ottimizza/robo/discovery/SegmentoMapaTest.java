package br.com.ottimizza.robo.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentoMapaTest {

    private static final JanelaAnos ANOS_2025_2026 = JanelaAnos.criar(2026);

    private static SegmentoMapa seg(String texto) {
        try {
            return SegmentoMapa.parse(texto);
        } catch (MapaInvalidoException e) {
            throw new AssertionError("esperava segmento valido: " + texto, e);
        }
    }

    private static String motivoDoErro(String texto) {
        MapaInvalidoException e = assertThrows(MapaInvalidoException.class, () -> SegmentoMapa.parse(texto));
        return e.getMessage();
    }

    @Test
    void literalCasaPorSubstringNormalizada() {
        SegmentoMapa segmento = seg("Declaracao Estaduais");

        assertTrue(segmento.aceita("Declaração Estaduais", null));
        assertTrue(segmento.aceita("DECLARACAO ESTADUAIS", null));
        assertFalse(segmento.aceita("Declaracao Federais", null));
    }

    @Test
    void literalIgnoraPontuacaoEEspacos() {
        assertTrue(seg("DCTFWEB - MIT").aceita("DCTFWEB-MIT", null));
        assertTrue(seg("1.Recibos").aceita("1. RECIBOS", null));
    }

    @Test
    void literalCasaNomeMaisLongoQueOTermo() {
        assertTrue(seg("Tributos").aceita("Tributos Municipais", null));
    }

    @Test
    void igualExigeNomeExato() {
        SegmentoMapa segmento = seg("=ECD");

        assertTrue(segmento.aceita("ECD", null));
        assertTrue(segmento.aceita("e.c.d.", null));
        assertFalse(segmento.aceita("ECD ANTIGO", null));
    }

    @Test
    void curingaCasaQualquerNome() {
        SegmentoMapa segmento = seg("*");

        assertTrue(segmento.aceita("Empresa A", null));
        assertTrue(segmento.aceita("2019", null));
    }

    @Test
    void negacaoRejeitaOTermoEAceitaOResto() {
        for (String texto : new String[] {"!DMED", "*!DMED"}) {
            SegmentoMapa segmento = seg(texto);

            assertFalse(segmento.aceita("DMED", null), texto);
            assertTrue(segmento.aceita("IRPF", null), texto);
        }
    }

    @Test
    void alternativasFuncionamComoOr() {
        SegmentoMapa segmento = seg("ECD@ECF@IBGE@SIMPLES");

        assertTrue(segmento.aceita("ECD", null));
        assertTrue(segmento.aceita("SIMPLES", null));
        assertFalse(segmento.aceita("DCTF", null));
    }

    /**
     * Duas negativas no mesmo segmento se somam como E: basta uma casar para rejeitar. E a forma
     * correta de excluir duas pastas - quebrar em duas linhas do mapa faz o oposto do pretendido
     * (ver {@code MapaScannerTest.duasLinhasComNegativasOpostasAnulamAExclusao}).
     */
    @Test
    void duasNegativasNoMesmoSegmentoExcluemAsDuas() {
        SegmentoMapa segmento = seg("!DMED@!TESTE");

        assertFalse(segmento.aceita("DMED", null));
        assertFalse(segmento.aceita("TESTE", null));
        assertTrue(segmento.aceita("IRPF", null));
    }

    @Test
    void positivaComNegativaFiltraDentroDoGrupo() {
        SegmentoMapa segmento = seg("TRIBUTOS@!MUNICIPAIS");

        assertTrue(segmento.aceita("Tributos Federais", null));
        assertFalse(segmento.aceita("Tributos Municipais", null));
        assertFalse(segmento.aceita("Protocolos", null));
    }

    /**
     * Armadilha real do casamento por substring: o termo tem de ser um trecho do nome da pasta,
     * entao "MUNICIPAL" nao alcanca a pasta "Municipais" (plural). Vale para o guia do suporte:
     * escreva o radical ("MUNICIPA"), nao o singular.
     */
    @Test
    void substringNaoCasaPluralDoTermo() {
        assertFalse(seg("MUNICIPAL").aceita("Tributos Municipais", null));
        assertTrue(seg("MUNICIPA").aceita("Tributos Municipais", null));
        assertTrue(seg("MUNICIPAIS").aceita("Tributos Municipais", null));
    }

    @Test
    void tokenAnoAceitaSoAnosDaJanela() {
        SegmentoMapa segmento = seg("$ANO");

        assertTrue(segmento.aceita("2025", ANOS_2025_2026));
        assertTrue(segmento.aceita("2026", ANOS_2025_2026));
        assertTrue(segmento.aceita("Ano 2026", ANOS_2025_2026));
        assertFalse(segmento.aceita("2024", ANOS_2025_2026));
        assertFalse(segmento.aceita("2027", ANOS_2025_2026));
    }

    @Test
    void tokenAnoRejeitaNomeComDoisAnosOuSemAno() {
        SegmentoMapa segmento = seg("$ANO");

        assertFalse(segmento.aceita("2025-2026", ANOS_2025_2026));
        assertFalse(segmento.aceita("GUIAS", ANOS_2025_2026));
        assertFalse(segmento.aceita("20265", ANOS_2025_2026));
    }

    @Test
    void tokenMesAnoAceitaAsGrafiasUsuais() {
        SegmentoMapa segmento = seg("$MES.ANO");

        assertTrue(segmento.aceita("01.2026", ANOS_2025_2026));
        assertTrue(segmento.aceita("12-2025", ANOS_2025_2026));
        assertTrue(segmento.aceita("012026", ANOS_2025_2026));
        assertTrue(segmento.aceita("1.2026", ANOS_2025_2026));
        assertTrue(segmento.aceita("11.2026", ANOS_2025_2026));
    }

    @Test
    void tokenMesAnoRejeitaMesInvalidoAnoForaDaJanelaEOrdemInvertida() {
        SegmentoMapa segmento = seg("$MES.ANO");

        assertFalse(segmento.aceita("13.2026", ANOS_2025_2026));
        assertFalse(segmento.aceita("00.2026", ANOS_2025_2026));
        assertFalse(segmento.aceita("01.2024", ANOS_2025_2026));
        assertFalse(segmento.aceita("2026", ANOS_2025_2026));
        assertFalse(segmento.aceita("2026.01", ANOS_2025_2026));
    }

    @Test
    void tokenAnoMesEOInversoDeMesAno() {
        SegmentoMapa segmento = seg("$ANO.MES");

        assertTrue(segmento.aceita("2026.01", ANOS_2025_2026));
        assertTrue(segmento.aceita("2025.12", ANOS_2025_2026));
        assertFalse(segmento.aceita("01.2026", ANOS_2025_2026));
        assertFalse(segmento.aceita("2026.13", ANOS_2025_2026));
    }

    @Test
    void tokenEReconhecidoIndependenteDeCaixa() {
        assertTrue(seg("$ano").aceita("2026", ANOS_2025_2026));
        assertTrue(seg("$Mes.Ano").aceita("03.2026", ANOS_2025_2026));
    }

    @Test
    void permiteNomeReservadoSoParaTextoExplicito() {
        assertTrue(seg("ENVIADOS").permiteNomeReservado());
        assertTrue(seg("=ENVIADOS").permiteNomeReservado());
        assertTrue(seg("ENVIADOS@ERROS").permiteNomeReservado());

        assertFalse(seg("*").permiteNomeReservado());
        assertFalse(seg("!DMED").permiteNomeReservado());
        assertFalse(seg("$ANO").permiteNomeReservado());
        assertFalse(seg("ENVIADOS@*").permiteNomeReservado());
        assertFalse(seg("ENVIADOS@!TEMP").permiteNomeReservado());
    }

    @Test
    void usaTokenDeAnoSoQuandoHaToken() {
        assertTrue(seg("$ANO").usaTokenDeAno());
        assertTrue(seg("GUIAS@$MES.ANO").usaTokenDeAno());
        assertFalse(seg("GUIAS").usaTokenDeAno());
        assertFalse(seg("*").usaTokenDeAno());
    }

    @Test
    void textoOriginalEPreservadoParaEcoNoModoDeTeste() {
        assertEquals("ECD@ECF", seg("  ECD@ECF  ").textoOriginal());
    }

    /**
     * O separador de alternativas era "|" e passou a ser "@". Como "|" e ilegal em nome de pasta
     * no Windows, um mapa escrito na sintaxe antiga pode ser recusado com mensagem clara em vez de
     * virar um literal que nao casa nada - que seria uma quebra silenciosa.
     */
    @Test
    void sintaxeAntigaComPipeERecusadaComOrientacao() {
        String motivo = motivoDoErro("ECD|ECF|IBGE");

        assertTrue(motivo.contains("|"), motivo);
        assertTrue(motivo.contains("@"), motivo);
    }

    @Test
    void asteriscoDuploERejeitadoComMensagemExplicita() {
        assertTrue(motivoDoErro("**").contains("nao e suportado"));
    }

    @Test
    void tokenDesconhecidoCitaOsTokensValidos() {
        String motivo = motivoDoErro("$AN0");

        assertTrue(motivo.contains("$AN0"));
        assertTrue(motivo.contains("$ANO"));
        assertTrue(motivo.contains("$MES.ANO"));
    }

    @Test
    void alternativaVaziaEInvalida() {
        assertTrue(motivoDoErro("A@@B").contains("alternativa vazia"));
        assertTrue(motivoDoErro("A@").contains("alternativa vazia"));
        assertTrue(motivoDoErro("@A").contains("alternativa vazia"));
    }

    @Test
    void segmentoVazioEInvalido() {
        assertTrue(motivoDoErro("   ").contains("segmento vazio"));
    }

    @Test
    void negacaoOuIgualdadeSemTermoSaoInvalidos() {
        assertTrue(motivoDoErro("!").contains("negacao sem termo"));
        assertTrue(motivoDoErro("=").contains("sem termo"));
        assertTrue(motivoDoErro("A@=").contains("sem termo"));
    }

    @Test
    void negarTudoEInvalido() {
        assertTrue(motivoDoErro("!*").contains("rejeitaria todas"));
    }
}
