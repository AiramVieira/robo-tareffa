package br.com.ottimizza.robo.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArvoreScannerTest {

    @Test
    void reproduzExemploDaSecao63(@TempDir Path raiz) throws IOException {
        // Nota: o readme ilustra a arvore com "EMPRESA INATIVA LTDA" mas o termo da regra e
        // "INATIVO" (masculino); usamos aqui o nome que de fato contem o termo da regra.
        criarPastas(raiz,
                "EMPRESA ATIVA LTDA/2025",
                "EMPRESA INATIVO LTDA/2025",
                "EMPRESA CONSOLIDADO LTDA/FILIAL SP/2025");

        Optional<List<CustomizacaoRule>> regras = CustomizacaoRule.parseAll("1:>!INATIVO;1:+CONSOLIDADO");
        assertTrue(regras.isPresent());

        ArvoreScanner scanner = new ArvoreScanner();
        List<Path> pastasAlvo = scanner.escanear(raiz, 2, regras.get(), FiltroAno.criar("", 2026));

        assertEquals(2, pastasAlvo.size());
        assertTrue(pastasAlvo.contains(raiz.resolve("EMPRESA ATIVA LTDA").resolve("2025")));
        assertTrue(pastasAlvo.contains(raiz.resolve("EMPRESA CONSOLIDADO LTDA").resolve("FILIAL SP").resolve("2025")));
    }

    @Test
    void niveisSubpastaZeroRetornaApenasARaiz(@TempDir Path raiz) throws IOException {
        criarPastas(raiz, "QUALQUER/COISA");

        ArvoreScanner scanner = new ArvoreScanner();
        List<Path> pastasAlvo = scanner.escanear(raiz, 0, List.of(), FiltroAno.criar("", 2026));

        assertEquals(List.of(raiz), pastasAlvo);
    }

    @Test
    void filtroDeAnoDescartaAnosForaDaTolerancia(@TempDir Path raiz) throws IOException {
        criarPastas(raiz, "EMPRESA/2024", "EMPRESA/2025", "EMPRESA/2026");

        ArvoreScanner scanner = new ArvoreScanner();
        FiltroAno filtroAno = FiltroAno.criar("2", 2026);
        List<Path> pastasAlvo = scanner.escanear(raiz, 2, List.of(), filtroAno);

        assertEquals(2, pastasAlvo.size());
        assertTrue(pastasAlvo.contains(raiz.resolve("EMPRESA").resolve("2025")));
        assertTrue(pastasAlvo.contains(raiz.resolve("EMPRESA").resolve("2026")));
    }

    private void criarPastas(Path raiz, String... caminhosRelativos) throws IOException {
        for (String caminho : caminhosRelativos) {
            Files.createDirectories(raiz.resolve(caminho));
        }
    }
}
