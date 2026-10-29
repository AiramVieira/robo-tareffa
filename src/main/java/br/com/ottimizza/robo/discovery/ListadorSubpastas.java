package br.com.ottimizza.robo.discovery;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * De onde o {@link MapaScanner} tira as subpastas de cada pasta. Em producao e sempre o disco
 * ({@link #doDisco}); o mapeador de pastas ({@code mapear.bat}) troca por uma listagem
 * memorizada, porque refaz a mesma varredura a cada ajuste que o analista faz na tela - e num
 * compartilhamento de rede cada listagem custa uma ida ao servidor.
 */
@FunctionalInterface
public interface ListadorSubpastas {

    /** Subpastas de {@code pasta}, ordenadas por nome. Falha de I/O vira lista vazia, nunca excecao. */
    List<Path> listar(Path pasta);

    static ListadorSubpastas doDisco(BiConsumer<String, Exception> avisoLog) {
        return pasta -> SubpastasListagem.listar(pasta, avisoLog);
    }
}
