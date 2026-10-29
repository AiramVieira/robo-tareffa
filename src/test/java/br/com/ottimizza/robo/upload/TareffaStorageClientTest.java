package br.com.ottimizza.robo.upload;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TareffaStorageClientTest {

    @Test
    void respostaComRecordESucesso() throws Exception {
        assertEquals(200, TareffaStorageClient.interpretarResposta(200,
                "{\"record\":{\"id\":\"QzpcVEFSRUZGQVxndWlhLnBkZg==\"},\"status\":\"success\"}"));
    }

    /** Resposta real do storage para token invalido: HTTP 200, erro so no corpo. */
    @Test
    void tokenRecusadoComHttp200ViraHttp401ParaDispararReautenticacao() throws Exception {
        assertEquals(401, TareffaStorageClient.interpretarResposta(200,
                "{\"error\":\"invalid_token\",\"message\":\"Token was not recognised\",\"status\":\"error\"}"));
    }

    @Test
    void tokenVencidoComHttp200ViraHttp401() throws Exception {
        assertEquals(401, TareffaStorageClient.interpretarResposta(200,
                "{\"error\":\"invalid_token\",\"message\":\"Token has expired\",\"status\":\"error\"}"));
    }

    @Test
    void outroErroComHttp200EFalhaComOCorpoNaMensagem() {
        IOException e = assertThrows(IOException.class, () -> TareffaStorageClient.interpretarResposta(200,
                "{\"error\":\"server_error\",\"message\":\"disco cheio\",\"status\":\"error\"}"));
        assertTrue(e.getMessage().contains("disco cheio"), e.getMessage());
    }

    @Test
    void corpoVazioComHttp200EFalha() {
        assertThrows(IOException.class, () -> TareffaStorageClient.interpretarResposta(200, ""));
    }

    @Test
    void corpoQueNaoEJsonComHttp200EFalha() {
        IOException e = assertThrows(IOException.class,
                () -> TareffaStorageClient.interpretarResposta(200, "<html>Bad Gateway</html>"));
        assertTrue(e.getMessage().contains("Bad Gateway"), e.getMessage());
    }

    @Test
    void statusHttpDeErroPassaAdiante() throws Exception {
        assertEquals(401, TareffaStorageClient.interpretarResposta(401, ""));
        assertEquals(500, TareffaStorageClient.interpretarResposta(500, "{\"status\":\"error\"}"));
    }
}
