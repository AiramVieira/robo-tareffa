package br.com.ottimizza.robo.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredenciaisAuthTest {

    private static final Map<String, String> AMBIENTE_COMPLETO = Map.of(
            "ROBO_AUTH_SERVER_URL", "https://auth.homologacao.exemplo",
            "ROBO_AUTH_CLIENT_ID", "cliente-homolog",
            "ROBO_AUTH_CLIENT_SECRET", "segredo-homolog",
            "ROBO_AUTH_SENHA_INTEGRACAO", "senha-homolog");

    private static UnaryOperator<String> ambiente(Map<String, String> variaveis) {
        return variaveis::get;
    }

    /** Um pacote de producao: os quatro blobs preenchidos. */
    private static CredenciaisAuth comBlobsPreenchidos(Map<String, String> variaveis) {
        return CredenciaisAuth.montar(ambiente(variaveis),
                SegredoEmbutido.cifrar("https://auth.producao.exemplo"),
                SegredoEmbutido.cifrar("cliente-prod"),
                SegredoEmbutido.cifrar("segredo-prod"),
                SegredoEmbutido.cifrar("senha-prod"));
    }

    /** Um pacote montado sem rodar o gerador: constantes ainda no placeholder. */
    private static CredenciaisAuth comBlobsVazios(Map<String, String> variaveis) {
        return CredenciaisAuth.montar(ambiente(variaveis), "", "", "", "");
    }

    @Test
    void semAmbienteUsaOsValoresEmbutidos() {
        CredenciaisAuth credenciais = comBlobsPreenchidos(Map.of());

        assertEquals("https://auth.producao.exemplo", credenciais.getAuthServerUrl());
        assertEquals("cliente-prod", credenciais.getClientId());
        assertEquals("segredo-prod", credenciais.getClientSecret());
        assertEquals("senha-prod", credenciais.getSenhaIntegracao());
    }

    @Test
    void variavelDeAmbienteGanhaDoValorEmbutido() {
        CredenciaisAuth credenciais = comBlobsPreenchidos(AMBIENTE_COMPLETO);

        assertEquals("https://auth.homologacao.exemplo", credenciais.getAuthServerUrl());
        assertEquals("cliente-homolog", credenciais.getClientId());
        assertEquals("segredo-homolog", credenciais.getClientSecret());
        assertEquals("senha-homolog", credenciais.getSenhaIntegracao());
    }

    /** Override parcial: o que o ambiente nao define continua vindo do jar. */
    @Test
    void overrideDeUmaVariavelNaoDerrubaAsOutras() {
        CredenciaisAuth credenciais = comBlobsPreenchidos(
                Map.of("ROBO_AUTH_SERVER_URL", "https://auth.homologacao.exemplo"));

        assertEquals("https://auth.homologacao.exemplo", credenciais.getAuthServerUrl());
        assertEquals("cliente-prod", credenciais.getClientId());
    }

    @Test
    void variavelEmBrancoNaoContaComoOverrideNemComoValor() {
        Map<String, String> comUmaVazia = new HashMap<>(AMBIENTE_COMPLETO);
        comUmaVazia.put("ROBO_AUTH_CLIENT_ID", "   ");

        IllegalStateException erro =
                assertThrows(IllegalStateException.class, () -> comBlobsVazios(comUmaVazia));

        assertTrue(erro.getMessage().contains("CLIENT_ID"), erro.getMessage());
    }

    /**
     * Sem ambiente e sem constantes preenchidas, o robo tem de parar no startup com uma mensagem
     * que aponte o repositorio - o erro e de quem montou o pacote, e nao ha nada que o cliente
     * possa corrigir na pasta de instalacao.
     */
    @Test
    void pacoteMontadoSemCredenciaisFalhaApontandoOGerador() {
        IllegalStateException erro =
                assertThrows(IllegalStateException.class, () -> comBlobsVazios(Map.of()));

        assertTrue(erro.getMessage().contains("CredenciaisEmbutidas"), erro.getMessage());
    }
}
