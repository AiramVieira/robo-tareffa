package br.com.ottimizza.robo.upload;

import java.io.IOException;

/**
 * Seam de upload usado pelo ciclo de processamento, para permitir testar a orquestracao de
 * envio (secao 7.4) sem depender de uma chamada HTTP real.
 */
public interface EnviadorArquivo {

    int enviar(String contabilidade, String nomeArquivo, byte[] conteudo, String token)
            throws IOException, InterruptedException;
}
