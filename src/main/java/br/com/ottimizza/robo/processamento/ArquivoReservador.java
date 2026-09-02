package br.com.ottimizza.robo.processamento;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Maquina de estados do arquivo em disco (secao 8): move para {@code ENVIADOS} inserindo o
 * marcador {@code " ARQUIVO ROBO"} (reserva, secao 7.2), e depois remove o marcador apos a
 * tentativa de envio, com sucesso ou falha (secao 7.5).
 */
public final class ArquivoReservador {

    public static final String MARCADOR = " ARQUIVO ROBO";

    public Path reservar(Path arquivoOriginal, Path pastaEnviados) throws IOException {
        Files.createDirectories(pastaEnviados);
        String nomeReservado = inserirMarcador(arquivoOriginal.getFileName().toString());
        Path destino = pastaEnviados.resolve(nomeReservado);
        return Files.move(arquivoOriginal, destino, StandardCopyOption.REPLACE_EXISTING);
    }

    public Path finalizar(Path arquivoReservado) throws IOException {
        String nomeOriginal = removerMarcador(arquivoReservado.getFileName().toString());
        Path destino = arquivoReservado.resolveSibling(nomeOriginal);
        return Files.move(arquivoReservado, destino, StandardCopyOption.REPLACE_EXISTING);
    }

    static String inserirMarcador(String nomeArquivo) {
        int posicaoPonto = nomeArquivo.lastIndexOf('.');
        if (posicaoPonto < 0) {
            return nomeArquivo + MARCADOR;
        }
        return nomeArquivo.substring(0, posicaoPonto) + MARCADOR + nomeArquivo.substring(posicaoPonto);
    }

    static String removerMarcador(String nomeArquivo) {
        return nomeArquivo.replace(MARCADOR, "");
    }
}
