package br.com.ottimizza.robo.processamento;

import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Resolucao dos caminhos usados no processamento de uma pasta-alvo (secao 7.1): pasta de
 * leitura ({@code PASTA_ENVIAR}, opcional), pasta de backup {@code ENVIADOS} (com ou sem
 * subdivisao por ano/mes) e a pasta de quarentena {@code ERROS}.
 */
public final class CaminhosPasta {

    private CaminhosPasta() {
    }

    public static Path pastaLeitura(Path pastaAlvo, String pastaEnviar) {
        return (pastaEnviar == null || pastaEnviar.isBlank()) ? pastaAlvo : pastaAlvo.resolve(pastaEnviar);
    }

    public static Path pastaEnviados(Path pastaAlvo, boolean enviadoDatado, LocalDate dataCiclo) {
        Path base = pastaAlvo.resolve("ENVIADOS");
        if (!enviadoDatado) {
            return base;
        }
        return base.resolve(String.valueOf(dataCiclo.getYear()))
                .resolve(String.format("%02d", dataCiclo.getMonthValue()));
    }

    public static Path pastaErros(Path pastaAlvo) {
        return pastaAlvo.resolve("ERROS");
    }
}
