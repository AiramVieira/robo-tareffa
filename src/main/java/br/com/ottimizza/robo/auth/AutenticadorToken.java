package br.com.ottimizza.robo.auth;

import java.io.IOException;

/**
 * Seam de autenticacao usado pelo ciclo de processamento, para permitir testar a orquestracao
 * de envio (secao 7.3/7.4) sem depender de uma chamada HTTP real.
 */
public interface AutenticadorToken {

    String obterTokenAtual(String contabilidade) throws IOException, InterruptedException;

    String reautenticar(String contabilidade) throws IOException, InterruptedException;
}
