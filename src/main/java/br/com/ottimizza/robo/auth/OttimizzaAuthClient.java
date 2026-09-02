package br.com.ottimizza.robo.auth;

import br.com.ottimizza.robo.config.CredenciaisAuth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Autenticacao OAuth2 "password grant" contra a API de autenticacao da Ottimizza (secao 7.3).
 * O token e obtido uma unica vez e reaproveitado; so e renovado quando explicitamente pedido
 * (apos um 401 do endpoint de upload, secao 7.4).
 */
public final class OttimizzaAuthClient implements AutenticadorToken {

    private final CredenciaisAuth credenciais;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String tokenAtual;

    public OttimizzaAuthClient(CredenciaisAuth credenciais, HttpClient httpClient) {
        this.credenciais = credenciais;
        this.httpClient = httpClient;
    }

    @Override
    public synchronized String obterTokenAtual(String contabilidade) throws IOException, InterruptedException {
        if (tokenAtual == null) {
            tokenAtual = autenticar(contabilidade);
        }
        return tokenAtual;
    }

    @Override
    public synchronized String reautenticar(String contabilidade) throws IOException, InterruptedException {
        tokenAtual = autenticar(contabilidade);
        return tokenAtual;
    }

    private String autenticar(String contabilidade) throws IOException, InterruptedException {
        String usuario = "integracao@" + contabilidade.toLowerCase(Locale.ROOT) + ".com.br";
        String basicAuth = Base64.getEncoder().encodeToString(
                (credenciais.getClientId() + ":" + credenciais.getClientSecret()).getBytes(StandardCharsets.UTF_8));

        String corpo = "grant_type=password"
                + "&username=" + URLEncoder.encode(usuario, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(credenciais.getSenhaIntegracao(), StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(credenciais.getAuthServerUrl() + "/oauth/token"))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Falha na autenticacao contra " + credenciais.getAuthServerUrl()
                    + ", status " + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());
        JsonNode accessToken = json.get("access_token");
        if (accessToken == null) {
            throw new IOException("Resposta de autenticacao sem access_token: " + response.body());
        }
        return accessToken.asText();
    }
}
