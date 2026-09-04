package br.com.ottimizza.robo.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParametrosLoaderTest {

    @Test
    void carregaConfiguracaoValidaEConverteContabilidadeParaTitleCase(@TempDir Path tempDir) throws IOException {
        Path arquivo = escrever(tempDir, """
                CONTABILIDADE=nomeresumido
                PASTA_INICIAL=C:/Tareffa
                NIVEIS_SUBPASTA=2
                """);

        Optional<Parametros> resultado = ParametrosLoader.carregar(arquivo);

        assertTrue(resultado.isPresent());
        assertEquals("Nomeresumido", resultado.get().getContabilidade());
        assertEquals("C:/Tareffa", resultado.get().getPastaInicial());
        assertEquals(2, resultado.get().getNiveisSubpasta());
        assertEquals(ParametrosLoader.INTERVALO_VARREDURA_PADRAO_SEGUNDOS, resultado.get().getIntervaloVarreduraSegundos());
        assertEquals(ParametrosLoader.INTERVALO_REMAPEAMENTO_PADRAO_HORAS, resultado.get().getIntervaloRemapeamentoHoras());
    }

    /**
     * O parque instalado tem VARIACAO_ANOS no parametros.txt. A chave nao existe mais, e a unica
     * coisa que nao pode acontecer e ela derrubar o ciclo: seria uma parada silenciosa em toda
     * instalacao no primeiro ciclo depois da atualizacao.
     */
    @Test
    void chaveVariacaoAnosDeInstalacaoAntigaEIgnoradaSemInvalidarOCiclo(@TempDir Path tempDir) throws IOException {
        Path arquivo = escrever(tempDir, """
                CONTABILIDADE=Escritorio
                PASTA_INICIAL=C:/Tareffa
                NIVEIS_SUBPASTA=2
                SUBNIVEL_ANO=2
                VARIACAO_ANOS=-3
                """);

        Optional<Parametros> resultado = ParametrosLoader.carregar(arquivo);

        assertTrue(resultado.isPresent());
        assertEquals("2", resultado.get().getSubnivelAno());
    }

    @Test
    void ignoraCicloQuandoContabilidadeVazia(@TempDir Path tempDir) throws IOException {
        Path arquivo = escrever(tempDir, """
                CONTABILIDADE=
                PASTA_INICIAL=C:/Tareffa
                NIVEIS_SUBPASTA=2
                """);

        assertTrue(ParametrosLoader.carregar(arquivo).isEmpty());
    }

    @Test
    void ignoraCicloQuandoPastaInicialSemDoisPontosBarra(@TempDir Path tempDir) throws IOException {
        Path arquivo = escrever(tempDir, """
                CONTABILIDADE=Empresa
                PASTA_INICIAL=Tareffa
                NIVEIS_SUBPASTA=2
                """);

        assertTrue(ParametrosLoader.carregar(arquivo).isEmpty());
    }

    @Test
    void ignoraCicloQuandoNiveisSubpastaNaoNumerico(@TempDir Path tempDir) throws IOException {
        Path arquivo = escrever(tempDir, """
                CONTABILIDADE=Empresa
                PASTA_INICIAL=C:/Tareffa
                NIVEIS_SUBPASTA=abc
                """);

        assertTrue(ParametrosLoader.carregar(arquivo).isEmpty());
    }

    @Test
    void preservaAcentuacaoUtf8DaContabilidade(@TempDir Path tempDir) throws IOException {
        // java.util.Properties.load(InputStream) le como ISO-8859-1 por padrao, corrompendo
        // acentos; o parser proprio (ArquivoChaveValor) le sempre como UTF-8.
        Path arquivo = escrever(tempDir, """
                CONTABILIDADE=contábil são paulo
                PASTA_INICIAL=C:/Tareffa
                NIVEIS_SUBPASTA=0
                """);

        Optional<Parametros> resultado = ParametrosLoader.carregar(arquivo);

        assertTrue(resultado.isPresent());
        assertEquals("Contábil São Paulo", resultado.get().getContabilidade());
    }

    @Test
    void aceitaCaminhoUncDeServidorDeArquivos(@TempDir Path tempDir) throws IOException {
        Path arquivo = escrever(tempDir, """
                CONTABILIDADE=Empresa
                PASTA_INICIAL=\\\\servidor\\contabil
                NIVEIS_SUBPASTA=2
                """);

        Optional<Parametros> resultado = ParametrosLoader.carregar(arquivo);

        assertTrue(resultado.isPresent());
        assertEquals("\\\\servidor\\contabil", resultado.get().getPastaInicial());
    }

    @Test
    void aceitaLetraDeUnidadeComBarraInvertida(@TempDir Path tempDir) throws IOException {
        Path arquivo = escrever(tempDir, """
                CONTABILIDADE=Empresa
                PASTA_INICIAL=C:\\Tareffa
                NIVEIS_SUBPASTA=2
                """);

        assertTrue(ParametrosLoader.carregar(arquivo).isPresent());
    }

    @Test
    void formasDePastaInicialAceitasERejeitadas() {
        assertTrue(ParametrosLoader.pastaInicialAceitavel("C:/Tareffa"));
        assertTrue(ParametrosLoader.pastaInicialAceitavel("C:\\Tareffa"));
        assertTrue(ParametrosLoader.pastaInicialAceitavel("W:/Clientes/Contabil"));
        assertTrue(ParametrosLoader.pastaInicialAceitavel("\\\\servidor\\contabil"));
        assertTrue(ParametrosLoader.pastaInicialAceitavel("//servidor/contabil"));

        assertFalse(ParametrosLoader.pastaInicialAceitavel("Tareffa"));
        assertFalse(ParametrosLoader.pastaInicialAceitavel("C:"));
        assertFalse(ParametrosLoader.pastaInicialAceitavel("C:/"));
        assertFalse(ParametrosLoader.pastaInicialAceitavel("\\\\servidor"));
        assertFalse(ParametrosLoader.pastaInicialAceitavel("\\\\servidor\\"));
        assertFalse(ParametrosLoader.pastaInicialAceitavel(""));
        assertFalse(ParametrosLoader.pastaInicialAceitavel(null));
    }

    private Path escrever(Path tempDir, String conteudo) throws IOException {
        Path arquivo = tempDir.resolve("parametros.txt");
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
        return arquivo;
    }
}
