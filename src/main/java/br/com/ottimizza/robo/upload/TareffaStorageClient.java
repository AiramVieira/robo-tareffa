package br.com.ottimizza.robo.upload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo))
                .build();

        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
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
