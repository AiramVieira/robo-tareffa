package br.com.ottimizza.robo.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegredoEmbutidoTest {

    @Test
    void oQueOGeradorCifraOJarDecifra() {
        String claro = "senha-de-integracao-com-acento-e-simbolo: ção#@!";

        assertEquals(claro, SegredoEmbutido.decifrar(SegredoEmbutido.cifrar(claro)));
    }

    /**
     * O IV e sorteado a cada cifragem, entao dois valores iguais nao produzem o mesmo blob. Sem
     * isso, alguem que comparasse dois pacotes conseguiria dizer "estes dois clientes usam a mesma
     * senha" so olhando as constantes.
     */
    @Test
    void cifrarDuasVezesOMesmoValorDaBlobsDiferentes() {
        assertNotEquals(SegredoEmbutido.cifrar("mesmo-valor"), SegredoEmbutido.cifrar("mesmo-valor"));
    }

    /** O valor cifrado nao pode aparecer legivel no blob - e o unico motivo de tudo isto existir. */
    @Test
    void oBlobNaoContemOTextoClaro() {
        assertFalse(SegredoEmbutido.cifrar("senha-supersecreta").contains("senha"));
    }

    /** Constante ainda nao preenchida: quem reclama e CredenciaisAuth, que sabe qual credencial e. */
    @Test
    void blobVazioViraTextoVazioEmVezDeExplodir() {
        assertEquals("", SegredoEmbutido.decifrar(""));
        assertEquals("", SegredoEmbutido.decifrar(null));
    }

    /**
     * Um blob truncado na hora de colar tem de parar o robo no startup, e nao virar lixo que so
     * aparece como 401 no primeiro upload - longe da causa.
     */
    @Test
    void blobCorrompidoFalhaAltoEComMensagemQueApontaOGerador() {
        String bom = SegredoEmbutido.cifrar("valor");
        String truncado = bom.substring(0, bom.length() - 6);

        IllegalStateException erro =
                assertThrows(IllegalStateException.class, () -> SegredoEmbutido.decifrar(truncado));
        assertTrue(erro.getMessage().contains("CredenciaisEmbutidas"), erro.getMessage());
    }
}
