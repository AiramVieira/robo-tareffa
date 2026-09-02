package br.com.ottimizza.robo.processamento;

import br.com.ottimizza.robo.auth.AutenticadorToken;
import br.com.ottimizza.robo.config.Parametros;
import br.com.ottimizza.robo.config.ParametrosLoader;
import br.com.ottimizza.robo.logging.ErroLogger;
import br.com.ottimizza.robo.upload.EnviadorArquivo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CicloProcessamentoTest {

    @TempDir
    private Path tempDir;

    private AutenticadorToken autenticador;
    private EnviadorArquivo enviador;
    private ErroLogger erroLogger;

    @BeforeEach
    void configurar() {
        autenticador = mock(AutenticadorToken.class);
        enviador = mock(EnviadorArquivo.class);
        erroLogger = new ErroLogger(tempDir.resolve("instalacao"));
    }

    private Parametros parametros(Path pastaInicial) throws IOException {
        Path arquivo = pastaInicial.getParent().resolve("parametros.txt");
        Files.writeString(arquivo, """
                CONTABILIDADE=Empresa
                PASTA_INICIAL=%s
                NIVEIS_SUBPASTA=0
                """.formatted(pastaInicial.toString().replace('\\', '/')), StandardCharsets.UTF_8);
        Optional<Parametros> resultado = ParametrosLoader.carregar(arquivo);
        assertTrue(resultado.isPresent());
        return resultado.get();
    }

    @Test
    void envioComSucessoRemoveMarcadorEDeixaArquivoEmEnviados() throws Exception {
        Path pastaAlvo = tempDir.resolve("cliente");
        Files.createDirectories(pastaAlvo);
        Files.writeString(pastaAlvo.resolve("guia.pdf"), "conteudo");

        when(autenticador.obterTokenAtual(anyString())).thenReturn("token-1");
        when(enviador.enviar(anyString(), anyString(), any(), anyString())).thenReturn(200);

        CicloProcessamento ciclo = new CicloProcessamento(autenticador, enviador, erroLogger);
        ciclo.processarPastaAlvo(pastaAlvo, parametros(pastaAlvo));

        Path enviado = pastaAlvo.resolve("ENVIADOS").resolve("guia.pdf");
        assertTrue(Files.exists(enviado));
        assertFalse(Files.exists(pastaAlvo.resolve("guia.pdf")));
        verify(enviador, times(1)).enviar(anyString(), anyString(), any(), anyString());
    }

    @Test
    void em401ReautenticaEReenviaComNovoToken() throws Exception {
        Path pastaAlvo = tempDir.resolve("cliente");
        Files.createDirectories(pastaAlvo);
        Files.writeString(pastaAlvo.resolve("guia.csv"), "conteudo");

        when(autenticador.obterTokenAtual(anyString())).thenReturn("token-velho");
        when(autenticador.reautenticar(anyString())).thenReturn("token-novo");
        when(enviador.enviar(anyString(), anyString(), any(), eq("token-velho"))).thenReturn(401);
        when(enviador.enviar(anyString(), anyString(), any(), eq("token-novo"))).thenReturn(200);

        CicloProcessamento ciclo = new CicloProcessamento(autenticador, enviador, erroLogger);
        ciclo.processarPastaAlvo(pastaAlvo, parametros(pastaAlvo));

        assertTrue(Files.exists(pastaAlvo.resolve("ENVIADOS").resolve("guia.csv")));
        verify(autenticador, times(1)).reautenticar(anyString());
        verify(enviador, times(2)).enviar(anyString(), anyString(), any(), anyString());
    }

    @Test
    void falhaNoEnvioAindaAssimRemoveMarcadorERegistraErro() throws Exception {
        Path pastaAlvo = tempDir.resolve("cliente");
        Files.createDirectories(pastaAlvo);
        Files.writeString(pastaAlvo.resolve("guia.txt"), "conteudo");

        when(autenticador.obterTokenAtual(anyString())).thenReturn("token-1");
        when(enviador.enviar(anyString(), anyString(), any(), anyString())).thenReturn(500);

        CicloProcessamento ciclo = new CicloProcessamento(autenticador, enviador, erroLogger);
        ciclo.processarPastaAlvo(pastaAlvo, parametros(pastaAlvo));

        Path enviado = pastaAlvo.resolve("ENVIADOS").resolve("guia.txt");
        assertTrue(Files.exists(enviado), "arquivo deve terminar em ENVIADOS mesmo com falha no envio");
        assertTrue(contemLinhaDeErro(tempDir.resolve("instalacao")));
    }

    @Test
    void extensaoNaoAceitaNaoEProcessada() throws Exception {
        Path pastaAlvo = tempDir.resolve("cliente");
        Files.createDirectories(pastaAlvo);
        Files.writeString(pastaAlvo.resolve("planilha.xlsx"), "conteudo");

        CicloProcessamento ciclo = new CicloProcessamento(autenticador, enviador, erroLogger);
        ciclo.processarPastaAlvo(pastaAlvo, parametros(pastaAlvo));

        assertTrue(Files.exists(pastaAlvo.resolve("planilha.xlsx")));
        assertFalse(Files.exists(pastaAlvo.resolve("ENVIADOS")));
    }

    private boolean contemLinhaDeErro(Path pastaInstalacao) throws IOException {
        Path diretorioErros = pastaInstalacao.resolve("logs").resolve("erros");
        if (!Files.isDirectory(diretorioErros)) {
            return false;
        }
        try (Stream<Path> arquivos = Files.list(diretorioErros)) {
            return arquivos.anyMatch(Files::isRegularFile);
        }
    }
}
