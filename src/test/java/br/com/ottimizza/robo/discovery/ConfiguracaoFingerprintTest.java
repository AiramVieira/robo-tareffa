package br.com.ottimizza.robo.discovery;

import br.com.ottimizza.robo.config.Parametros;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConfiguracaoFingerprintTest {

    private static Parametros base() {
        return new Parametros("Escritorio", "C:/Tareffa", 2, 30, 24, "ENVIAR", true,
                "1:>!INATIVO", "2");
    }

    private static String fp(Parametros parametros) {
        return ConfiguracaoFingerprint.calcular(parametros, "", 2026);
    }

    @Test
    void eDeterministico() {
        assertEquals(fp(base()), fp(base()));
    }

    @Test
    void mudarQualquerCampoDeDescobertaMudaOFingerprint() {
        String original = fp(base());

        assertNotEquals(original, fp(new Parametros("Escritorio", "D:/Tareffa", 2, 30, 24, "ENVIAR", true, "1:>!INATIVO", "2")));
        assertNotEquals(original, fp(new Parametros("Escritorio", "C:/Tareffa", 3, 30, 24, "ENVIAR", true, "1:>!INATIVO", "2")));
        assertNotEquals(original, fp(new Parametros("Escritorio", "C:/Tareffa", 2, 30, 24, "OUTRA", true, "1:>!INATIVO", "2")));
        assertNotEquals(original, fp(new Parametros("Escritorio", "C:/Tareffa", 2, 30, 24, "ENVIAR", true, "1:>!TESTE", "2")));
        assertNotEquals(original, fp(new Parametros("Escritorio", "C:/Tareffa", 2, 30, 24, "ENVIAR", true, "1:>!INATIVO", "3")));
    }

    /**
     * Uma edicao que nao muda a lista de pastas nao deve custar uma varredura completa da arvore -
     * que num compartilhamento de rede leva minutos e bloqueia o envio de arquivos.
     */
    @Test
    void camposQueNaoAfetamADescobertaNaoMudamOFingerprint() {
        String original = fp(base());

        assertEquals(original, fp(new Parametros("Escritorio", "C:/Tareffa", 2, 60, 24, "ENVIAR", true, "1:>!INATIVO", "2")));
        assertEquals(original, fp(new Parametros("Escritorio", "C:/Tareffa", 2, 30, 48, "ENVIAR", true, "1:>!INATIVO", "2")));
        assertEquals(original, fp(new Parametros("Escritorio", "C:/Tareffa", 2, 30, 24, "ENVIAR", false, "1:>!INATIVO", "2")));
        assertEquals(original, fp(new Parametros("Outro Nome", "C:/Tareffa", 2, 30, 24, "ENVIAR", true, "1:>!INATIVO", "2")));
    }

    @Test
    void oTextoBrutoDoMapaEntraNoFingerprint() {
        String semMapa = ConfiguracaoFingerprint.calcular(base(), "", 2026);
        String comMapa = ConfiguracaoFingerprint.calcular(base(), "Cliente/$ANO\n", 2026);
        String mapaEditado = ConfiguracaoFingerprint.calcular(base(), "Cliente/$ANO\nOutro/$ANO\n", 2026);
        String mapaSoComentarioNovo = ConfiguracaoFingerprint.calcular(base(), "# nota\nCliente/$ANO\n", 2026);

        assertNotEquals(semMapa, comMapa);
        assertNotEquals(comMapa, mapaEditado);
        assertNotEquals(comMapa, mapaSoComentarioNovo);
    }

    @Test
    void nuloEVazioNoMapaSaoOMesmoModo() {
        assertEquals(ConfiguracaoFingerprint.calcular(base(), null, 2026),
                ConfiguracaoFingerprint.calcular(base(), "", 2026));
    }

    /**
     * Sem o ano no fingerprint, um cache gerado em 31/12 continuaria valido por ate 24 h com a
     * janela do ano anterior - e as pastas do ano novo ficariam invisiveis por um dia.
     */
    @Test
    void oAnoCorrenteEntraNoFingerprint() {
        assertNotEquals(ConfiguracaoFingerprint.calcular(base(), "", 2026),
                ConfiguracaoFingerprint.calcular(base(), "", 2027));
    }

    @Test
    void oResultadoEUmHashHexadecimal() {
        assertEquals(64, fp(base()).length());
        assertEquals(fp(base()).toLowerCase(java.util.Locale.ROOT), fp(base()));
    }
}
