package br.com.ottimizza.robo.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Registro de erros (secao 4 do readme): gravado em {@code <instalacao>/logs/erros}, um
 * arquivo por dia. Falhas de envio nao interrompem o robo; apenas ficam aqui para consulta.
 */
public final class ErroLogger {

    private final Path diretorioErros;

    public ErroLogger(Path pastaInstalacao) {
        this.diretorioErros = pastaInstalacao.resolve("logs").resolve("erros");
    }

    public synchronized void registrar(String mensagem, Throwable causa) {
        try {
            Files.createDirectories(diretorioErros);
            Path arquivo = diretorioErros.resolve(LocalDate.now() + ".log");
            String linha = "[" + Instant.now() + "] " + mensagem
                    + (causa != null ? " - " + causa : "")
                    + System.lineSeparator();
            Files.writeString(arquivo, linha, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Falha ao gravar log de erro: " + e.getMessage());
        }
    }
}
