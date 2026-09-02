package br.com.ottimizza.robo.processamento;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Normaliza para minuscula a extensao de todos os arquivos de uma pasta (secao 7.1, passo 3),
 * antes de qualquer filtro de extensao aceita. Arquivos de 0 bytes nao sao renomeados.
 */
public final class ExtensaoNormalizador {

    public void normalizar(Path pastaLeitura) throws IOException {
        List<Path> arquivos;
        try (Stream<Path> stream = Files.list(pastaLeitura)) {
            arquivos = stream.filter(Files::isRegularFile).collect(Collectors.toList());
        }
        for (Path arquivo : arquivos) {
            normalizarUm(arquivo);
        }
    }

    private void normalizarUm(Path arquivo) throws IOException {
        if (Files.size(arquivo) == 0) {
            return;
        }
        String nome = arquivo.getFileName().toString();
        int posicaoPonto = nome.lastIndexOf('.');
        if (posicaoPonto < 0) {
            return;
        }
        String extensao = nome.substring(posicaoPonto);
        String extensaoMinuscula = extensao.toLowerCase(Locale.ROOT);
        if (extensao.equals(extensaoMinuscula)) {
            return;
        }
        Path destino = arquivo.resolveSibling(nome.substring(0, posicaoPonto) + extensaoMinuscula);
        renomearForcandoCaixa(arquivo, destino);
    }

    /**
     * Em filesystems case-insensitive-mas-case-preserving (NTFS), um Files.move direto entre
     * dois nomes que so diferem em caixa e tratado como o "mesmo arquivo": a chamada reporta
     * sucesso, mas o nome no disco permanece com a caixa original. Passar por um nome
     * intermediario forca duas renomeacoes reais, garantindo a troca de caixa.
     */
    private void renomearForcandoCaixa(Path origem, Path destino) throws IOException {
        Path intermediario = origem.resolveSibling(origem.getFileName().toString() + ".robo-tmp-caixa");
        Files.move(origem, intermediario, StandardCopyOption.REPLACE_EXISTING);
        Files.move(intermediario, destino, StandardCopyOption.REPLACE_EXISTING);
    }
}
