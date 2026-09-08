package br.com.ottimizza.robo.config;

/**
 * Os quatro segredos de autenticacao, cifrados, embutidos no {@code robo.jar}.
 *
 * <p><b>Constantes GERADAS - nao edite a mao.</b> Para trocar qualquer valor (rotacao de senha,
 * apontar para outro ambiente), rode o gerador e substitua o bloco inteiro:
 *
 * <pre>
 *   mvnw.cmd -o test-compile
 *   java -cp target/classes;target/test-classes br.com.ottimizza.robo.config.GerarCredenciaisEmbutidas
 * </pre>
 *
 * <p>O gerador pergunta os quatro valores e imprime este bloco pronto para colar. Ele vive em
 * {@code src/test/java} de proposito: fontes de teste nao entram no jar, entao a maquina do cliente
 * recebe so o que decifra, nunca a ferramenta que cifra.
 *
 * <p>Leia {@link SegredoEmbutido} antes de confiar nisto para algo alem do que foi pedido: e
 * ofuscacao contra leitura casual, nao sigilo contra o cliente.
 */
final class CredenciaisEmbutidas {

    static final String AUTH_SERVER_URL = "PvON/JPlJ60qwtbjOMYtOPPPsGj17C6haNeVFSxdjRFbuq+3vxZ2BfNbdg/nA3ZqbhaqC32PAHLxsrtQr7G29PWFI2cghcjKbFOOIA==";
    static final String CLIENT_ID = "O++zznpxGupb5Cs53APXckGqTwWq+F62P+1n7z2eX1I7Wj7YDQpD7dAtFlANYwa8";
    static final String CLIENT_SECRET = "cozY6pitIf3fFcZcUWBFQxL0Jp/Uutv1NNj0raduCUXCdWFWzElWCCCJIGswYpfSKj8woVYjjBS2n++M1EkZ11Ec2OY=";
    static final String SENHA_INTEGRACAO = "I98d2+zdH9/OwgA7rc262GZJI/GRvh2IDuSDJ/LtGwdN";

    private CredenciaisEmbutidas() {
    }
}
