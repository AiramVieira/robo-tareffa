package br.com.ottimizza.robo.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Listagem ordenada das subpastas de uma pasta, compartilhada pelos dois modos de descoberta
 * ({@link ArvoreScanner} e {@link MapaScanner}).
 *
 * <p>Uma falha de I/O nunca interrompe a varredura: e registrada como aviso e a pasta e tratada
 * como sem subpastas. Numa arvore em compartilhamento de rede, uma pasta sem permissao e comum, e
 * derrubar o ciclo por causa dela deixaria todos os outros clientes sem envio.
 */
final class SubpastasListagem {

    private SubpastasListagem() {
    }

    static List<Path> listar(Path pasta, BiConsumer<String, Exception> avisoLog) {
        try (Stream<Path> stream = Files.list(pasta)) {
            return stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            avisoLog.accept("Falha ao listar subpastas de " + pasta, e);
            return List.of();
        }
    }
}
