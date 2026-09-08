package br.com.ottimizza.robo.config;

import java.util.function.UnaryOperator;

/**
 * Credenciais fixas usadas na autenticacao OAuth2 "password grant" contra a API da Ottimizza
 * (secao 7.3 do readme): URL do servidor, client-id/client-secret (Basic Auth) e a senha fixa
 * de integracao.
 *
 * <p>Sao as mesmas em toda instalacao, entao deixaram de ser configuracao: viajam cifradas dentro
 * do proprio {@code robo.jar} ({@link CredenciaisEmbutidas}). O arquivo
 * {@code credenciais.properties} - que expunha a senha de integracao em texto puro na pasta de
 * instalacao, ao alcance de qualquer pessoa do cliente - nao existe mais, nem no pacote nem no
 * instalador.
 *
 * <p>Sobra <b>um</b> ponto de override, {@code ROBO_AUTH_*} no ambiente do processo, para apontar
 * uma execucao de desenvolvimento contra outro servidor sem recompilar. Nao e um caminho que o
 * cliente use: definir variavel de ambiente de um servico do Windows exige mexer no registro ou
 * no {@code winsw.xml}, e nada na documentacao dele menciona isso.
 *
 * <p>Sobre o limite dessa protecao, leia {@link SegredoEmbutido}: e ofuscacao contra leitura
 * casual, nao sigilo contra um cliente decidido a extrair a credencial.
 */
public final class CredenciaisAuth {

    private final String authServerUrl;
    private final String clientId;
    private final String clientSecret;
    private final String senhaIntegracao;

    CredenciaisAuth(String authServerUrl, String clientId, String clientSecret, String senhaIntegracao) {
        this.authServerUrl = authServerUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.senhaIntegracao = senhaIntegracao;
    }

    public static CredenciaisAuth carregar() {
        return montar(System::getenv);
    }

    /** O ambiente entra por parametro para que o override seja testavel sem mexer no processo. */
    static CredenciaisAuth montar(UnaryOperator<String> ambiente) {
        return montar(ambiente, CredenciaisEmbutidas.AUTH_SERVER_URL, CredenciaisEmbutidas.CLIENT_ID,
                CredenciaisEmbutidas.CLIENT_SECRET, CredenciaisEmbutidas.SENHA_INTEGRACAO);
    }

    /**
     * Os blobs tambem entram por parametro para que os testes nao dependam de
     * {@link CredenciaisEmbutidas} estar preenchido. Sem isso, os casos "pacote montado sem
     * credenciais" passavam so enquanto as constantes estivessem vazias e quebravam no commit que
     * as preenchesse - um teste que testa o estado do repositorio, e nao o codigo.
     */
    static CredenciaisAuth montar(UnaryOperator<String> ambiente, String urlCifrada,
                                   String clientIdCifrado, String clientSecretCifrado,
                                   String senhaCifrada) {
        String authServerUrl = resolver(ambiente, "ROBO_AUTH_SERVER_URL", urlCifrada);
        String clientId = resolver(ambiente, "ROBO_AUTH_CLIENT_ID", clientIdCifrado);
        String clientSecret = resolver(ambiente, "ROBO_AUTH_CLIENT_SECRET", clientSecretCifrado);
        String senhaIntegracao = resolver(ambiente, "ROBO_AUTH_SENHA_INTEGRACAO", senhaCifrada);

        StringBuilder faltando = new StringBuilder();
        if (isBlank(authServerUrl)) faltando.append("AUTH_SERVER_URL ");
        if (isBlank(clientId)) faltando.append("CLIENT_ID ");
        if (isBlank(clientSecret)) faltando.append("CLIENT_SECRET ");
        if (isBlank(senhaIntegracao)) faltando.append("SENHA_INTEGRACAO ");

        if (faltando.length() > 0) {
            // Um pacote montado sem as constantes preenchidas. E um erro de build, nao do cliente -
            // a mensagem tem de mandar quem le de volta para o repositorio, e nao para a pasta de
            // instalacao, onde nao ha nada a corrigir.
            throw new IllegalStateException(
                    "Este robo.jar foi montado sem as credenciais embutidas: " + faltando.toString().trim()
                            + ". Rode GerarCredenciaisEmbutidas, cole o bloco em CredenciaisEmbutidas.java"
                            + " e remonte o pacote.");
        }

        return new CredenciaisAuth(authServerUrl, clientId, clientSecret, senhaIntegracao);
    }

    private static String resolver(UnaryOperator<String> ambiente, String variavelAmbiente,
                                    String blobCifrado) {
        String doAmbiente = ambiente.apply(variavelAmbiente);
        if (doAmbiente != null && !doAmbiente.isBlank()) {
            return doAmbiente.trim();
        }
        return SegredoEmbutido.decifrar(blobCifrado).trim();
    }

    private static boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }

    public String getAuthServerUrl() {
        return authServerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getSenhaIntegracao() {
        return senhaIntegracao;
    }
}
