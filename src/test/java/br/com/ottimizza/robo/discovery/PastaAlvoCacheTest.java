package br.com.ottimizza.robo.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PastaAlvoCacheTest {

    private static final String NOME_ARQUIVO = ".robo_pastas_alvo.cache.json";

    @Test
    void gravaEReleAsPastasComOFingerprint(@TempDir Path raiz) throws IOException {
        PastaAlvoCache cache = new PastaAlvoCache(raiz);
        cache.salvar(List.of("C:/Tareffa/A", "C:/Tareffa/B"), Instant.now(), "abc123");

        PastaAlvoCache.CacheData dados = cache.ler().orElseThrow();

        assertEquals(List.of("C:/Tareffa/A", "C:/Tareffa/B"), dados.pastas);
        assertEquals("abc123", dados.configFingerprint);
        assertTrue(cache.fingerprintCoincide(dados, "abc123"));
        assertFalse(cache.fingerprintCoincide(dados, "outro"));
    }

    @Test
    void arquivoAusenteVemVazio(@TempDir Path raiz) {
        assertTrue(new PastaAlvoCache(raiz).ler().isEmpty());
    }

    /**
     * Cache gravado por uma versao anterior do robo nao tem o campo. Tratar {@code null} como
     * "coincide" faria um cliente que ja editou as regras continuar servindo a lista antiga
     * indefinidamente; o custo de tratar como divergente e um remapeamento extra na atualizacao.
     */
    @Test
    void cacheAntigoSemOCampoNaoCoincideComNenhumFingerprint(@TempDir Path raiz) throws IOException {
        Files.writeString(raiz.resolve(NOME_ARQUIVO), """
                {
                  "pastas" : [ "C:/Tareffa/A" ],
                  "geradoEm" : "2026-09-03T10:00:00Z"
                }
                """, StandardCharsets.UTF_8);
        PastaAlvoCache cache = new PastaAlvoCache(raiz);

        PastaAlvoCache.CacheData dados = cache.ler().orElseThrow();

        assertEquals(List.of("C:/Tareffa/A"), dados.pastas);
        assertFalse(cache.fingerprintCoincide(dados, "qualquer"));
        assertFalse(cache.fingerprintCoincide(dados, null));
    }

    @Test
    void fingerprintEmBrancoTambemNaoCoincide(@TempDir Path raiz) throws IOException {
        PastaAlvoCache cache = new PastaAlvoCache(raiz);
        cache.salvar(List.of("C:/Tareffa/A"), Instant.now(), "   ");

        assertFalse(cache.fingerprintCoincide(cache.ler().orElseThrow(), "   "));
    }

    /** Um jar antigo reinstalado no cliente tem de conseguir ler um cache mais novo. */
    @Test
    void campoDesconhecidoNoJsonNaoTornaOCacheIlegivel(@TempDir Path raiz) throws IOException {
        Files.writeString(raiz.resolve(NOME_ARQUIVO), """
                {
                  "pastas" : [ "C:/Tareffa/A" ],
                  "geradoEm" : "2026-09-03T10:00:00Z",
                  "configFingerprint" : "abc123",
                  "campoDeUmaVersaoFutura" : 42
                }
                """, StandardCharsets.UTF_8);

        Optional<PastaAlvoCache.CacheData> dados = new PastaAlvoCache(raiz).ler();

        assertTrue(dados.isPresent());
        assertEquals("abc123", dados.get().configFingerprint);
    }

    @Test
    void jsonCorrompidoVemVazioSemExcecao(@TempDir Path raiz) throws IOException {
        Files.writeString(raiz.resolve(NOME_ARQUIVO), "{ nao e json", StandardCharsets.UTF_8);

        assertTrue(new PastaAlvoCache(raiz).ler().isEmpty());
    }

    @Test
    void expiraExatamenteNaHoraConfigurada(@TempDir Path raiz) throws IOException {
        PastaAlvoCache cache = new PastaAlvoCache(raiz);

        cache.salvar(List.of("A"), Instant.now().minus(23, ChronoUnit.HOURS), "fp");
        assertFalse(cache.expirado(cache.ler().orElseThrow(), 24));

        cache.salvar(List.of("A"), Instant.now().minus(24, ChronoUnit.HOURS), "fp");
        assertTrue(cache.expirado(cache.ler().orElseThrow(), 24));
    }

    @Test
    void geradoEmIlegivelContaComoExpirado(@TempDir Path raiz) throws IOException {
        Files.writeString(raiz.resolve(NOME_ARQUIVO), """
                { "pastas" : [ "A" ], "geradoEm" : "ontem", "configFingerprint" : "fp" }
                """, StandardCharsets.UTF_8);
        PastaAlvoCache cache = new PastaAlvoCache(raiz);

        assertTrue(cache.expirado(cache.ler().orElseThrow(), 24));
    }
}
