package br.com.ottimizza.robo.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltroAnoTest {

    @Test
    void exemploDaSecao64AceitaAnoAtualEUmAnoAtras() {
        FiltroAno filtro = FiltroAno.criar("2", 2026);

        assertTrue(filtro.caminhoAceito("C:/Tareffa/Empresa/2025"));
        assertTrue(filtro.caminhoAceito("C:/Tareffa/Empresa/2026"));
        assertFalse(filtro.caminhoAceito("C:/Tareffa/Empresa/2024"));
    }

    @Test
    void anoFuturoNaoEntraNaJanelaFixa() {
        FiltroAno filtro = FiltroAno.criar("2", 2026);

        assertFalse(filtro.caminhoAceito(".../2027"));
    }

    @Test
    void aJanelaAcompanhaAViradaDeAno() {
        FiltroAno filtro = FiltroAno.criar("2", 2027);

        assertTrue(filtro.caminhoAceito(".../2026"));
        assertTrue(filtro.caminhoAceito(".../2027"));
        assertFalse(filtro.caminhoAceito(".../2025"));
    }

    @Test
    void subnivelZeroUsaNivelFinalDoRamo() {
        FiltroAno filtro = FiltroAno.criar("0", 2026);
        assertTrue(filtro.isUsaNivelFinal());
        assertTrue(filtro.isAtivo());
    }

    @Test
    void subnivelUltimoUsaNivelFinalDoRamo() {
        FiltroAno filtro = FiltroAno.criar("ÚLTIMO", 2026);
        assertTrue(filtro.isUsaNivelFinal());
    }

    @Test
    void semSubnivelAnoFiltroFicaInativoENaoRestringeNada() {
        FiltroAno filtro = FiltroAno.criar("", 2026);
        assertFalse(filtro.isAtivo());
        assertTrue(filtro.caminhoAceito(".../2000"));
    }
}
