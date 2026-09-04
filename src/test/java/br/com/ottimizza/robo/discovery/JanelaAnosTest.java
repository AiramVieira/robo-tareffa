package br.com.ottimizza.robo.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JanelaAnosTest {

    @Test
    void aceitaSempreOAnoCorrenteEOAnterior() {
        JanelaAnos janela = JanelaAnos.criar(2026);

        assertEquals(2025, janela.minimo());
        assertEquals(2026, janela.maximo());
        assertTrue(janela.contem(2025));
        assertTrue(janela.contem(2026));
    }

    @Test
    void naoAceitaAnoAnteriorAoAnteriorNemAnoFuturo() {
        JanelaAnos janela = JanelaAnos.criar(2026);

        assertFalse(janela.contem(2024));
        assertFalse(janela.contem(2027));
    }

    @Test
    void aJanelaAcompanhaAViradaDeAno() {
        JanelaAnos janela = JanelaAnos.criar(2027);

        assertTrue(janela.contem(2026));
        assertTrue(janela.contem(2027));
        assertFalse(janela.contem(2025));
    }

    @Test
    void textoContemAnoAceitoOlhaOTextoInteiro() {
        JanelaAnos janela = JanelaAnos.criar(2026);

        assertTrue(janela.textoContemAnoAceito("C:/Tareffa/Empresa/2025"));
        assertTrue(janela.textoContemAnoAceito("C:/Tareffa/2026/Empresa/GUIAS"));
        assertFalse(janela.textoContemAnoAceito("C:/Tareffa/Empresa/2024"));
    }

    @Test
    void descricaoResumeAJanela() {
        assertEquals("2025..2026", JanelaAnos.criar(2026).descricao());
        assertEquals("2026..2027", JanelaAnos.criar(2027).descricao());
    }
}
