package br.com.ottimizza.robo.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Le arquivos de configuracao no formato simples {@code CHAVE=valor}, uma entrada por linha.
 * Nao usa {@link java.util.Properties} porque seu escaping de barra invertida corromperia
 * valores com caminhos UNC do Windows (ex.: {@code \\servidor\pasta}).
 */
public final class ArquivoChaveValor {

    /**
     * Marca de ordem de byte que o Bloco de Notas insere ao salvar como "UTF-8 com BOM". Sem
     * remove-la, a primeira chave do arquivo viria com o caractere invisivel colado no nome e
     * seria lida como ausente - o robo ignoraria todos os ciclos sem dizer o motivo.
     */
    private static final char BOM = '\uFEFF';

    private ArquivoChaveValor() {
    }

    /** Remove a BOM do inicio da linha, se presente. Publico porque todo arquivo de
     * configuracao editado no Bloco de Notas precisa do mesmo tratamento. */
    public static String removerBom(String linha) {
        return !linha.isEmpty() && linha.charAt(0) == BOM ? linha.substring(1) : linha;
    }

    public static Map<String, String> ler(Path arquivo) {
        Map<String, String> resultado = new LinkedHashMap<>();
        if (arquivo == null || !Files.isRegularFile(arquivo)) {
            return resultado;
        }
        try {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            for (String linha : linhas) {
                String semEspacos = removerBom(linha).trim();
                if (semEspacos.isEmpty() || semEspacos.startsWith("#")) {
                    continue;
                }
                int posicaoIgual = semEspacos.indexOf('=');
                if (posicaoIgual < 0) {
                    continue;
                }
                String chave = semEspacos.substring(0, posicaoIgual).trim().toUpperCase(Locale.ROOT);
                String valor = semEspacos.substring(posicaoIgual + 1).trim();
                if (!chave.isEmpty()) {
                    resultado.put(chave, valor);
                }
            }
        } catch (IOException e) {
            // Arquivo ilegivel: tratado como se estivesse vazio; as validacoes obrigatorias
            // do chamador (ex.: ParametrosLoader) vao rejeitar a configuracao resultante.
        }
        return resultado;
    }
}
