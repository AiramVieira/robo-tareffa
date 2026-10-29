package br.com.ottimizza.robo.upload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Envio (upload) do arquivo para o storage do Tareffa (secao 7.4): POST multipart/form-data
 * com um unico campo {@code file}, token no cabecalho {@code Authorization} sem prefixo
 * "Bearer ".
 */
public final class TareffaStorageClient implements EnviadorArquivo {

    private static final String URL_TEMPLATE =
            "https://s3.tareffaapp.com.br:55325/storage/tareffa-gestao-servicos/accounting/%s/store";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    public TareffaStorageClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public int enviar(String contabilidade, String nomeArquivo, byte[] conteudo, String token)
            throws IOException, InterruptedException {
        String boundary = "----RoboTareffaBoundary" + System.nanoTime();
        byte[] corpo = construirMultipart(boundary, nomeArquivo, conteudo);
        String url = String.format(URL_TEMPLATE, URLEncoder.encode(contabilidade, StandardCharsets.UTF_8));

        // Uma conexao por envio, sem reaproveitar conexao ociosa: o storage fecha conexao parada
        // aos ~60s, e reaproveitar uma no instante em que ele a fechava fazia o POST falhar com
        // "HTTP/1.1 header parser received no bytes" (o JDK so repete sozinho metodo
        // idempotente). Exige jdk.httpclient.allowRestrictedHeaders=connection (RoboService).
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", token)
                .header("Connection", "close")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return interpretarResposta(response.statusCode(), response.body());
    }

    /**
     * O storage nao sinaliza falha pelo status HTTP: token invalido ou vencido volta como 200, com
     * o erro no corpo ({@code {"error":"invalid_token","message":"Token was not recognised",
     * "status":"error"}}). So o corpo diz se o arquivo foi gravado - o sucesso traz o registro
     * criado em {@code record}, o mesmo campo que os demais clientes do storage leem.
     *
     * <p>Sem esta leitura, todo envio feito depois de o token vencer (1h apos a autenticacao)
     * contava como sucesso: o arquivo ia para ENVIADOS sem chegar ao Tareffa, sem nada no log de
     * erros, e a renovacao de token por 401 (secao 7.4) nunca disparava. Por isso o token recusado
     * no corpo vira 401 aqui, e qualquer outra resposta sem {@code record} vira excecao com o corpo,
     * para o motivo chegar ao log de erros.
     */
    static int interpretarResposta(int status, String corpo) throws IOException {
        if (status < 200 || status >= 300) {
            return status;
        }

        JsonNode json;
        try {
            json = OBJECT_MAPPER.readTree(corpo == null ? "" : corpo);
        } catch (JsonProcessingException e) {
            throw new IOException("Storage respondeu HTTP " + status + " com corpo que nao e JSON: " + resumir(corpo));
        }

        if (json.path("record").isObject()) {
            return status;
        }
        if ("invalid_token".equals(json.path("error").asText())) {
            return HttpURLConnection.HTTP_UNAUTHORIZED;
        }
        throw new IOException("Storage nao gravou o arquivo (HTTP " + status + "): " + resumir(corpo));
    }

    private static String resumir(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return "(vazio)";
        }
        return corpo.length() <= 300 ? corpo : corpo.substring(0, 300) + "...";
    }

    private byte[] construirMultipart(String boundary, String nomeArquivo, byte[] conteudo) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();

        String cabecalho = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + nomeArquivo + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        saida.write(cabecalho.getBytes(StandardCharsets.UTF_8));
        saida.write(conteudo);

        String rodape = "\r\n--" + boundary + "--\r\n";
        saida.write(rodape.getBytes(StandardCharsets.UTF_8));

        return saida.toByteArray();
    }
}
