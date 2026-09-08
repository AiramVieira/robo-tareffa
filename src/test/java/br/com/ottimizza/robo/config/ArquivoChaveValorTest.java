package br.com.ottimizza.robo.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArquivoChaveValorTest {

    private static Map<String, String> ler(Path pasta, String conteudo) throws IOException {
        Path arquivo = pasta.resolve("parametros.txt");
        Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
        return ArquivoChaveValor.ler(arquivo);
    }

    /**
     * O suporte edita esses arquivos no Bloco de Notas, que oferece "UTF-8 com BOM". Sem remover a
     * BOM, a primeira chave viria com um caractere invisivel colado no nome e seria lida como
     * ausente - o robo ignorava todos os ciclos com a mensagem generica "configuracao invalida".
     */
    @Test
    void bomUtf8NaPrimeiraLinhaNaoEscondeAPrimeiraChave(@TempDir Path pasta) throws IOException {
        Map<String, String> chaves = ler(pasta, '\uFEFF' + """
                CONTABILIDADE=Escritorio
                PASTA_INICIAL=C:/Tareffa
                """);

        assertEquals("Escritorio", chaves.get("CONTABILIDADE"));
        assertEquals("C:/Tareffa", chaves.get("PASTA_INICIAL"));
    }

    @Test
    void chaveEMaiusculaEValorPreservaCaixaEEspacosInternos(@TempDir Path pasta) throws IOException {
        Map<String, String> chaves = ler(pasta, "  contabilidade = Nome Do Escritorio  \n");

        assertEquals("Nome Do Escritorio", chaves.get("CONTABILIDADE"));
    }

    @Test
    void comentariosELinhasSemIgualSaoIgnorados(@TempDir Path pasta) throws IOException {
        Map<String, String> chaves = ler(pasta, """
                # comentario
                linha solta sem igual
                CONTABILIDADE=Escritorio
                """);

        assertEquals(1, chaves.size());
    }

    @Test
    void caminhoUncNaoEDesfiguradoPeloEscapingDeBarraInvertida(@TempDir Path pasta) throws IOException {
        Map<String, String> chaves = ler(pasta, "PASTA_INICIAL=\\\\servidor\\contabil\n");

        assertEquals("\\\\servidor\\contabil", chaves.get("PASTA_INICIAL"));
    }

    @Test
    void arquivoAusenteVemVazioSemExcecao() {
        assertTrue(ArquivoChaveValor.ler(Path.of("nao-existe-parametros.txt")).isEmpty());
        assertTrue(ArquivoChaveValor.ler(null).isEmpty());
    }

    @Test
    void removerBomSoAgeNoInicioDaLinha() {
        assertEquals("CHAVE", ArquivoChaveValor.removerBom('\uFEFF' + "CHAVE"));
        assertEquals("CHAVE", ArquivoChaveValor.removerBom("CHAVE"));
        assertEquals("", ArquivoChaveValor.removerBom(""));
        assertEquals("CHA" + '\uFEFF' + "VE", ArquivoChaveValor.removerBom("CHA" + '\uFEFF' + "VE"));
    }
}
