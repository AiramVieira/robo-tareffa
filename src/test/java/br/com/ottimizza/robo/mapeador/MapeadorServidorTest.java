package br.com.ottimizza.robo.mapeador;

import br.com.ottimizza.robo.config.Parametros;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapeadorServidorTest {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private MapeadorServidor servidor;

    @AfterEach
    void encerrar() {
        if (servidor != null) {
            servidor.encerrar("fim do teste");
        }
    }

    private MapeadorServidor iniciar(Path instalacao, Path raiz) throws IOException {
        Parametros parametros = new Parametros("Escritorio", raiz.toString(), 2, 30, 1, "", false, "", "");
        servidor = MapeadorServidor.iniciar(new MapeadorApi(instalacao, parametros));
        return servidor;
    }

    private URI uri(String rota) {
        return URI.create("http://127.0.0.1:" + servidor.porta() + rota);
    }

    private HttpResponse<String> get(String rota, String token) throws IOException, InterruptedException {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(uri(rota)).GET();
        if (token != null) {
            pedido.header("X-Token", token);
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String rota, String corpo) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(uri(rota))
                        .header("X-Token", servidor.token())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(corpo)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void entregaAPaginaSemToken(@TempDir Path instalacao, @TempDir Path raiz) throws Exception {
        iniciar(instalacao, raiz);

        HttpResponse<String> resposta = get("/", null);

        assertEquals(200, resposta.statusCode());
        assertTrue(resposta.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(resposta.body().contains("Mapeador de pastas"));
    }

    @Test
    void apiSemTokenOuComTokenErradoERecusada(@TempDir Path instalacao, @TempDir Path raiz) throws Exception {
        iniciar(instalacao, raiz);

        assertEquals(403, get("/api/estado", null).statusCode());
        assertEquals(403, get("/api/estado", "0".repeat(48)).statusCode());
    }

    @Test
    void apiComTokenResponde(@TempDir Path instalacao, @TempDir Path raiz) throws Exception {
        Files.createDirectories(raiz.resolve("Cliente A"));
        iniciar(instalacao, raiz);

        HttpResponse<String> estado = get("/api/estado", servidor.token());
        HttpResponse<String> subpastas = get("/api/subpastas?caminho=", servidor.token());
        HttpResponse<String> previa = post("/api/previa", "{\"texto\":\"*\"}");

        assertEquals(200, estado.statusCode());
        assertTrue(estado.body().contains("\"contabilidade\":\"Escritorio\""), estado.body());
        assertTrue(subpastas.body().contains("\"nome\":\"Cliente A\""), subpastas.body());
        assertTrue(previa.body().contains("\"totalDeAlvos\":1"), previa.body());
    }

    @Test
    void caminhoForaDaArvoreEPedidoMalFormadoViram400(@TempDir Path instalacao, @TempDir Path raiz) throws Exception {
        iniciar(instalacao, raiz);

        assertEquals(400, get("/api/subpastas?caminho=..%2F..", servidor.token()).statusCode());
        assertEquals(400, post("/api/previa", "isto nao e json").statusCode());
    }

    @Test
    void metodoErradoViraErro405(@TempDir Path instalacao, @TempDir Path raiz) throws Exception {
        iniciar(instalacao, raiz);

        assertEquals(405, get("/api/salvar", servidor.token()).statusCode());
    }

    /**
     * Defesa contra DNS rebinding: um site malicioso que aponte o proprio dominio para 127.0.0.1
     * chegaria aqui com o Host dele. O HttpClient nao deixa trocar o Host, entao vai por socket.
     */
    @Test
    void hostDiferenteDoEnderecoLocalERecusado(@TempDir Path instalacao, @TempDir Path raiz) throws Exception {
        iniciar(instalacao, raiz);

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), servidor.porta())) {
            OutputStream saida = socket.getOutputStream();
            saida.write(("GET /api/estado HTTP/1.1\r\nHost: atacante.example:" + servidor.porta()
                    + "\r\nX-Token: " + servidor.token() + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            saida.flush();
            InputStream entrada = socket.getInputStream();
            String resposta = new String(entrada.readAllBytes(), StandardCharsets.US_ASCII);
            assertTrue(resposta.startsWith("HTTP/1.1 403"), resposta);
        }
    }

    @Test
    void encerrarPelaTelaLiberaQuemEstaAguardando(@TempDir Path instalacao, @TempDir Path raiz) throws Exception {
        iniciar(instalacao, raiz);
        CompletableFuture<String> motivo = CompletableFuture.supplyAsync(() -> {
            try {
                return servidor.aguardarEncerramento();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        });

        assertEquals(200, post("/api/encerrar", "{}").statusCode());

        assertEquals("encerrado pela tela", motivo.get(5, TimeUnit.SECONDS));
    }
}
