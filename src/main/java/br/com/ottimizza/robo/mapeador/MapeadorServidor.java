package br.com.ottimizza.robo.mapeador;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servidor HTTP local da tela do mapeador: entrega a pagina e traduz cada chamada {@code /api/...}
 * para o {@link MapeadorApi}.
 *
 * <p>Seguranca, porque ele roda no computador do cliente e enxerga a arvore de pastas dele:
 * <ul>
 *   <li>escuta so em {@code 127.0.0.1}, numa porta aleatoria - outras maquinas da rede nao
 *       alcancam;</li>
 *   <li>toda chamada {@code /api} exige o token gerado nesta execucao (vai no endereco aberto no
 *       navegador, depois do {@code #}). Sem ele, qualquer site aberto no navegador do cliente
 *       poderia chamar {@code localhost} e listar as pastas ou gravar o mapa;</li>
 *   <li>o cabecalho {@code Host} e conferido, contra DNS rebinding.</li>
 * </ul>
 *
 * <p>Encerra quando a tela pede ({@code /api/encerrar}), quando a janela do {@code mapear.bat} e
 * fechada (o processo morre junto) ou depois de {@link #OCIOSIDADE_MAXIMA} sem nenhuma chamada - a
 * tela aberta manda um sinal de vida a cada minuto, entao isso so acontece com a aba fechada.
 */
final class MapeadorServidor {

    static final Duration OCIOSIDADE_MAXIMA = Duration.ofMinutes(30);

    private static final int LIMITE_CORPO = 1024 * 1024;
    private static final String RECURSO_PAGINA = "/mapeador/index.html";

    record PedidoTexto(String texto) {
    }

    record PedidoNomes(List<String> prefixo, String segmento) {
    }

    record Erro(String erro) {
    }

    private final MapeadorApi api;
    private final HttpServer servidor;
    private final ExecutorService atendentes;
    private final ScheduledExecutorService vigia;
    private final String token;
    private final byte[] pagina;
    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final AtomicLong ultimoAcesso = new AtomicLong(System.nanoTime());
    private final AtomicReference<String> motivoDoEncerramento = new AtomicReference<>();
    private final CountDownLatch encerrado = new CountDownLatch(1);

    private MapeadorServidor(MapeadorApi api, HttpServer servidor, String token, byte[] pagina) {
        this.api = api;
        this.servidor = servidor;
        this.token = token;
        this.pagina = pagina;
        // Mais de uma thread: a previa de uma arvore de rede grande pode levar minutos, e a tela
        // continua pedindo subpastas para o explorador enquanto isso.
        this.atendentes = Executors.newFixedThreadPool(4, tarefa -> {
            Thread thread = new Thread(tarefa, "mapeador-http");
            thread.setDaemon(true);
            return thread;
        });
        this.vigia = Executors.newSingleThreadScheduledExecutor(tarefa -> {
            Thread thread = new Thread(tarefa, "mapeador-vigia");
            thread.setDaemon(true);
            return thread;
        });
    }

    static MapeadorServidor iniciar(MapeadorApi api) throws IOException {
        byte[] pagina;
        try (InputStream entrada = MapeadorServidor.class.getResourceAsStream(RECURSO_PAGINA)) {
            if (entrada == null) {
                throw new IllegalStateException(RECURSO_PAGINA + " nao esta dentro do robo.jar");
            }
            pagina = entrada.readAllBytes();
        }

        byte[] bytesDoToken = new byte[24];
        new SecureRandom().nextBytes(bytesDoToken);

        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        MapeadorServidor servidor = new MapeadorServidor(api, http, HexFormat.of().formatHex(bytesDoToken), pagina);
        http.createContext("/", servidor::atender);
        http.setExecutor(servidor.atendentes);
        http.start();

        long verificacao = Math.max(1, OCIOSIDADE_MAXIMA.toMinutes() / 10);
        servidor.vigia.scheduleAtFixedRate(servidor::encerrarSeOcioso, verificacao, verificacao, TimeUnit.MINUTES);
        return servidor;
    }

    int porta() {
        return servidor.getAddress().getPort();
    }

    String token() {
        return token;
    }

    /** O endereco a abrir no navegador. O token vai no fragmento, que o navegador nunca envia em requisicao. */
    String endereco() {
        return "http://127.0.0.1:" + porta() + "/#" + token;
    }

    /** Bloqueia ate o servidor encerrar, e devolve o motivo. */
    String aguardarEncerramento() throws InterruptedException {
        encerrado.await();
        return motivoDoEncerramento.get();
    }

    void encerrar(String motivo) {
        if (!motivoDoEncerramento.compareAndSet(null, motivo)) {
            return;
        }
        // Em outra thread: quem pede o encerramento pode ser o proprio atendimento de
        // /api/encerrar, e o stop() espera os atendimentos em andamento terminarem.
        Thread parada = new Thread(() -> {
            servidor.stop(1);
            atendentes.shutdownNow();
            vigia.shutdownNow();
            encerrado.countDown();
        }, "mapeador-parada");
        parada.setDaemon(true);
        parada.start();
    }

    private void encerrarSeOcioso() {
        long parado = System.nanoTime() - ultimoAcesso.get();
        if (parado > OCIOSIDADE_MAXIMA.toNanos()) {
            encerrar("nenhum acesso ha " + OCIOSIDADE_MAXIMA.toMinutes() + " minutos (a aba do navegador foi fechada?)");
        }
    }

    // ------------------------------------------------------------------------------ atendimento

    private void atender(HttpExchange troca) {
        try {
            if (!hostValido(troca.getRequestHeaders().getFirst("Host"))) {
                responderJson(troca, 403, new Erro("Host nao permitido"));
                return;
            }
            String rota = troca.getRequestURI().getPath();
            if (rota.equals("/")) {
                exigirMetodo(troca, "GET");
                responder(troca, 200, "text/html; charset=utf-8", pagina);
                return;
            }
            if (!rota.startsWith("/api/")) {
                responderJson(troca, 404, new Erro("nao encontrado: " + rota));
                return;
            }
            if (!tokenValido(troca.getRequestHeaders().getFirst("X-Token"))) {
                responderJson(troca, 403, new Erro("token ausente ou invalido - abra o endereco que o mapear.bat mostrou"));
                return;
            }
            ultimoAcesso.set(System.nanoTime());
            responderJson(troca, 200, executar(troca, rota));
        } catch (MetodoNaoPermitido e) {
            responderSemFalhar(troca, 405, new Erro(e.getMessage()));
        } catch (IllegalArgumentException e) {
            responderSemFalhar(troca, 400, new Erro(e.getMessage()));
        } catch (RotaDesconhecida e) {
            responderSemFalhar(troca, 404, new Erro(e.getMessage()));
        } catch (Exception e) {
            responderSemFalhar(troca, 500, new Erro(e.toString()));
        } finally {
            troca.close();
        }
    }

    private Object executar(HttpExchange troca, String rota) throws IOException {
        switch (rota) {
            case "/api/estado" -> {
                exigirMetodo(troca, "GET");
                return api.estado();
            }
            case "/api/subpastas" -> {
                exigirMetodo(troca, "GET");
                return api.subpastas(parametroDaQuery(troca, "caminho"));
            }
            case "/api/nomes-no-nivel" -> {
                exigirMetodo(troca, "POST");
                PedidoNomes pedido = lerCorpo(troca, PedidoNomes.class);
                return api.nomesNoNivel(pedido.prefixo() == null ? List.of() : pedido.prefixo(), pedido.segmento());
            }
            case "/api/previa" -> {
                exigirMetodo(troca, "POST");
                return api.previa(lerCorpo(troca, PedidoTexto.class).texto());
            }
            case "/api/salvar" -> {
                exigirMetodo(troca, "POST");
                return api.salvar(lerCorpo(troca, PedidoTexto.class).texto());
            }
            case "/api/atualizar-pastas" -> {
                exigirMetodo(troca, "POST");
                api.esquecerPastas();
                return Map.of("ok", true);
            }
            case "/api/ping" -> {
                exigirMetodo(troca, "POST");
                return Map.of("ok", true);
            }
            case "/api/encerrar" -> {
                exigirMetodo(troca, "POST");
                encerrar("encerrado pela tela");
                return Map.of("ok", true);
            }
            default -> throw new RotaDesconhecida("nao encontrado: " + rota);
        }
    }

    private boolean hostValido(String host) {
        return host != null && (host.equals("127.0.0.1:" + porta()) || host.equalsIgnoreCase("localhost:" + porta()));
    }

    private boolean tokenValido(String recebido) {
        return recebido != null && MessageDigest.isEqual(
                recebido.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private static void exigirMetodo(HttpExchange troca, String metodo) {
        if (!metodo.equals(troca.getRequestMethod())) {
            throw new MetodoNaoPermitido(troca.getRequestMethod() + " nao e aceito aqui (use " + metodo + ")");
        }
    }

    private <T> T lerCorpo(HttpExchange troca, Class<T> tipo) throws IOException {
        byte[] corpo = troca.getRequestBody().readNBytes(LIMITE_CORPO + 1);
        if (corpo.length > LIMITE_CORPO) {
            throw new IllegalArgumentException("pedido grande demais");
        }
        try {
            return json.readValue(corpo, tipo);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("pedido mal formado: " + e.getOriginalMessage());
        }
    }

    private static String parametroDaQuery(HttpExchange troca, String nome) {
        String query = troca.getRequestURI().getRawQuery();
        if (query == null) {
            return "";
        }
        for (String par : query.split("&")) {
            int igual = par.indexOf('=');
            String chave = igual < 0 ? par : par.substring(0, igual);
            if (chave.equals(nome)) {
                return igual < 0 ? "" : URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private void responderJson(HttpExchange troca, int status, Object corpo) throws IOException {
        responder(troca, status, "application/json; charset=utf-8", json.writeValueAsBytes(corpo));
    }

    private void responderSemFalhar(HttpExchange troca, int status, Object corpo) {
        try {
            responderJson(troca, status, corpo);
        } catch (IOException | RuntimeException ignorada) {
            // A resposta ja tinha comecado a sair, ou o navegador desistiu da conexao.
        }
    }

    private static void responder(HttpExchange troca, int status, String tipo, byte[] corpo) throws IOException {
        troca.getResponseHeaders().set("Content-Type", tipo);
        troca.getResponseHeaders().set("Cache-Control", "no-store");
        troca.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        troca.getResponseHeaders().set("X-Frame-Options", "DENY");
        troca.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        troca.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline';"
                + " style-src 'unsafe-inline'; img-src data:; connect-src 'self'; base-uri 'none';"
                + " form-action 'none'; frame-ancestors 'none'");
        troca.sendResponseHeaders(status, corpo.length == 0 ? -1 : corpo.length);
        if (corpo.length > 0) {
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(corpo);
            }
        }
    }

    private static final class MetodoNaoPermitido extends RuntimeException {
        MetodoNaoPermitido(String mensagem) {
            super(mensagem);
        }
    }

    private static final class RotaDesconhecida extends RuntimeException {
        RotaDesconhecida(String mensagem) {
            super(mensagem);
        }
    }
}
