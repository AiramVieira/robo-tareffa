package br.com.ottimizza.robo.processamento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensaoNormalizadorTest {

    private final ExtensaoNormalizador normalizador = new ExtensaoNormalizador();

    @Test
    void normalizaExtensaoParaMinusculo(@TempDir Path pasta) throws IOException {
        Files.writeString(pasta.resolve("NOTA.PDF"), "conteudo");

        normalizador.normalizar(pasta);

        // Files.exists() e case-insensitive no Windows/NTFS e nao pegaria uma renomeacao que
        // "funcionou" sem de fato trocar a caixa no disco; comparamos o nome real listado.
        assertEquals("NOTA.pdf", nomeUnicoArquivoEm(pasta));
    }

    private String nomeUnicoArquivoEm(Path pasta) throws IOException {
        try (Stream<Path> stream = Files.list(pasta)) {
            return stream.findFirst().orElseThrow().getFileName().toString();
        }
    }

    @Test
    void naoRenomeiaArquivoDeZeroBytes(@TempDir Path pasta) throws IOException {
        Files.createFile(pasta.resolve("VAZIO.PDF"));

        normalizador.normalizar(pasta);

        assertTrue(Files.exists(pasta.resolve("VAZIO.PDF")));
    }
}
