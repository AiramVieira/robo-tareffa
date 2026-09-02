package br.com.ottimizza.robo.processamento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArquivoReservadorTest {

    private final ArquivoReservador reservador = new ArquivoReservador();

    @Test
    void reservaInsereMarcadorEFinalizarRestauraNomeOriginal(@TempDir Path tempDir) throws IOException {
        Path origem = tempDir.resolve("origem");
        Files.createDirectories(origem);
        Path arquivo = origem.resolve("guia_pagamento.pdf");
        Files.writeString(arquivo, "conteudo");

        Path enviados = tempDir.resolve("ENVIADOS");

        Path reservado = reservador.reservar(arquivo, enviados);
        assertEquals("guia_pagamento ARQUIVO ROBO.pdf", reservado.getFileName().toString());
        assertTrue(Files.exists(reservado));
        assertFalse(Files.exists(arquivo));

        Path finalizado = reservador.finalizar(reservado);
        assertEquals("guia_pagamento.pdf", finalizado.getFileName().toString());
        assertTrue(Files.exists(finalizado));
        assertFalse(Files.exists(reservado));
    }

    @Test
    void arquivoSemExtensaoRecebeMarcadorNoFinalDoNome(@TempDir Path tempDir) throws IOException {
        Path origem = tempDir.resolve("origem");
        Files.createDirectories(origem);
        Path arquivo = origem.resolve("guia_sem_extensao");
        Files.writeString(arquivo, "conteudo");

        Path reservado = reservador.reservar(arquivo, tempDir.resolve("ENVIADOS"));
        assertEquals("guia_sem_extensao ARQUIVO ROBO", reservado.getFileName().toString());
    }
}
