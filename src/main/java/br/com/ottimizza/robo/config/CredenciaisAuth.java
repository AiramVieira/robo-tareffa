package br.com.ottimizza.robo.config;

import java.nio.file.Path;
import java.util.Map;

/**
 * Credenciais fixas usadas na autenticacao OAuth2 "password grant" contra a API da Ottimizza
 * (secao 7.3 do readme): URL do servidor, client-id/client-secret (Basic Auth) e a senha fixa
 * de integracao.
 *
 * <p>Nao sao lidas de {@code parametros.txt} (que e sobre o comportamento de varredura de pastas,
 * nao sobre segredos) nem hardcoded no codigo-fonte. Sao resolvidas, nesta ordem:
 * <ol>
 *   <li>variaveis de ambiente {@code ROBO_AUTH_SERVER_URL}, {@code ROBO_AUTH_CLIENT_ID},
 *       {@code ROBO_AUTH_CLIENT_SECRET}, {@code ROBO_AUTH_SENHA_INTEGRACAO};</li>
 *   <li>arquivo {@code credenciais.properties} (formato {@code CHAVE=valor}) na pasta de
 *       instalacao do robo, que nao deve ser versionado.</li>
 * </ol>
 */
public final class CredenciaisAuth {

    private final String authServerUrl;
    private final String clientId;
    private final String clientSecret;
    private final String senhaIntegracao;

    private CredenciaisAuth(String authServerUrl, String clientId, String clientSecret, String senhaIntegracao) {
        this.authServerUrl = authServerUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.senhaIntegracao = senhaIntegracao;
    }

    public static CredenciaisAuth carregar(Path pastaInstalacao) {
        Map<String, String> doArquivo = ArquivoChaveValor.ler(pastaInstalacao.resolve("credenciais.properties"));

        String authServerUrl = resolver("ROBO_AUTH_SERVER_URL", doArquivo, "AUTH_SERVER_URL");
        String clientId = resolver("ROBO_AUTH_CLIENT_ID", doArquivo, "CLIENT_ID");
        String clientSecret = resolver("ROBO_AUTH_CLIENT_SECRET", doArquivo, "CLIENT_SECRET");
        String senhaIntegracao = resolver("ROBO_AUTH_SENHA_INTEGRACAO", doArquivo, "SENHA_INTEGRACAO");

        StringBuilder faltando = new StringBuilder();
        if (isBlank(authServerUrl)) faltando.append("AUTH_SERVER_URL ");
        if (isBlank(clientId)) faltando.append("CLIENT_ID ");
        if (isBlank(clientSecret)) faltando.append("CLIENT_SECRET ");
        if (isBlank(senhaIntegracao)) faltando.append("SENHA_INTEGRACAO ");

        if (faltando.length() > 0) {
            throw new IllegalStateException(
                    "Credenciais de autenticacao ausentes: " + faltando.toString().trim()
                            + ". Configure as variaveis de ambiente ROBO_AUTH_* ou o arquivo "
                            + pastaInstalacao.resolve("credenciais.properties"));
        }

        return new CredenciaisAuth(authServerUrl, clientId, clientSecret, senhaIntegracao);
    }

    private static String resolver(String variavelAmbiente, Map<String, String> doArquivo, String chaveArquivo) {
        String doAmbiente = System.getenv(variavelAmbiente);
        if (doAmbiente != null && !doAmbiente.isBlank()) {
            return doAmbiente.trim();
        }
        return doArquivo.getOrDefault(chaveArquivo, "").trim();
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
