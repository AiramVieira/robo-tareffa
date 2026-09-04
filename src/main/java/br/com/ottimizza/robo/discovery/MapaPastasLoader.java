package br.com.ottimizza.robo.discovery;

import br.com.ottimizza.robo.config.ArquivoChaveValor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Le e valida {@code mapa-pastas.txt} (secao 6.6 do readme).
 *
 * <p>Contrato deliberado: {@link Optional#empty()} significa <b>somente</b> "o arquivo nao
 * existe" (modo {@code NIVEIS_SUBPASTA}). Qualquer outro problema - inclusive
 * {@link IOException} e "o arquivo existe mas nao rende nenhum template" - e
 * {@link Resultado.Invalido}, porque cair em silencio no modo antigo faria o robo varrer pastas
 * erradas e <b>mover arquivos do cliente</b> de lugar, sem undo.
 */
public final class MapaPastasLoader {

    public static final String NOME_ARQUIVO = "mapa-pastas.txt";

    /** Teto defensivo contra um {@code dir /s} colado por acidente dentro do arquivo. */
    static final int MAXIMO_TEMPLATES = 200;
    static final int MAXIMO_SEGMENTOS = 20;

    private MapaPastasLoader() {
    }

    public sealed interface Resultado permits Resultado.Carregado, Resultado.Invalido {
        record Carregado(MapaPastas mapa) implements Resultado {
        }

        record Invalido(String motivo) implements Resultado {
        }
    }

    /**
     * @param pastaInicial valor cru de {@code PASTA_INICIAL}, para validar templates escritos com
     *                     o caminho absoluto do cliente (a transcricao literal da arvore)
     */
    public static Optional<Resultado> carregar(Path arquivoMapa, String pastaInicial) {
        if (arquivoMapa == null || !Files.isRegularFile(arquivoMapa)) {
            return Optional.empty();
        }

        String textoBruto;
        try {
            textoBruto = Files.readString(arquivoMapa, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Optional.of(new Resultado.Invalido(
                    NOME_ARQUIVO + ": nao foi possivel ler o arquivo (" + e.getMessage() + ")"));
        }

        return Optional.of(interpretar(textoBruto, pastaInicial));
    }

    static Resultado interpretar(String textoBruto, String pastaInicial) {
        List<TemplateMapa> templates = new ArrayList<>();
        String[] linhas = textoBruto.split("\r\n|\r|\n", -1);

        for (int i = 0; i < linhas.length; i++) {
            int numeroLinha = i + 1;
            String linha = ArquivoChaveValor.removerBom(linhas[i]).trim();
            if (linha.isEmpty() || linha.startsWith("#")) {
                continue;
            }
            try {
                templates.add(parseTemplate(linha, numeroLinha, pastaInicial));
            } catch (MapaInvalidoException e) {
                return new Resultado.Invalido(
                        NOME_ARQUIVO + " linha " + numeroLinha + ": " + e.getMessage());
            }
            if (templates.size() > MAXIMO_TEMPLATES) {
                return new Resultado.Invalido(NOME_ARQUIVO + ": mais de " + MAXIMO_TEMPLATES
                        + " templates - o arquivo deve descrever a estrutura de pastas, nao listar"
                        + " todas as pastas existentes");
            }
        }

        if (templates.isEmpty()) {
            return new Resultado.Invalido(NOME_ARQUIVO
                    + ": nenhum template (todas as linhas sao comentario ou vazias) - descomente as"
                    + " linhas do mapa, ou apague/renomeie o arquivo para voltar ao modo"
                    + " NIVEIS_SUBPASTA");
        }
        return new Resultado.Carregado(new MapaPastas(templates, textoBruto));
    }

    private static TemplateMapa parseTemplate(String linha, int numeroLinha, String pastaInicial)
            throws MapaInvalidoException {
        String caminho = removerPrefixoDaRaiz(linha, pastaInicial);

        List<String> partes = separarSegmentos(caminho);
        if (partes.isEmpty()) {
            throw new MapaInvalidoException("template sem nenhum segmento apos a raiz");
        }
        if (partes.size() > MAXIMO_SEGMENTOS) {
            throw new MapaInvalidoException("template com " + partes.size() + " niveis (maximo "
                    + MAXIMO_SEGMENTOS + ")");
        }

        List<SegmentoMapa> segmentos = new ArrayList<>(partes.size());
        for (String parte : partes) {
            segmentos.add(SegmentoMapa.parse(parte));
        }
        return new TemplateMapa(segmentos, numeroLinha, linha);
    }

    /**
     * Separa por {@code /} ou {@code \} e descarta o separador final. Nenhum dos dois pode
     * aparecer em nome de pasta no Windows, entao aceitar os dois nao cria ambiguidade - e o
     * suporte vai colar caminhos copiados do Explorer.
     */
    private static List<String> separarSegmentos(String caminho) throws MapaInvalidoException {
        String semSeparadorFinal = caminho;
        while (semSeparadorFinal.endsWith("/") || semSeparadorFinal.endsWith("\\")) {
            semSeparadorFinal = semSeparadorFinal.substring(0, semSeparadorFinal.length() - 1).trim();
        }

        List<String> partes = new ArrayList<>();
        for (String parte : semSeparadorFinal.split("[/\\\\]", -1)) {
            if (parte.trim().isEmpty()) {
                throw new MapaInvalidoException("segmento vazio (duas barras seguidas?) em \""
                        + caminho + "\"");
            }
            partes.add(parte);
        }
        return partes;
    }

    /**
     * Um template pode comecar pelo caminho absoluto do cliente ({@code W:/Departamento Fiscal/...}
     * ou {@code \\servidor\pasta\...}) - a transcricao literal da arvore e o valor central do
     * desenho. Nesse caso a raiz e validada contra {@code PASTA_INICIAL} e removida; aceitar e
     * descartar em silencio esconderia um erro de configuracao.
     */
    private static String removerPrefixoDaRaiz(String linha, String pastaInicial)
            throws MapaInvalidoException {
        if (!temPrefixoAbsoluto(linha)) {
            return linha;
        }
        if (pastaInicial == null || pastaInicial.isBlank()) {
            throw new MapaInvalidoException("template comeca por um caminho absoluto, mas"
                    + " PASTA_INICIAL esta vazia");
        }

        String raizNormalizada = normalizarCaminho(pastaInicial);
        String linhaNormalizada = normalizarCaminho(linha);

        if (!linhaNormalizada.equals(raizNormalizada) && !linhaNormalizada.startsWith(raizNormalizada + "/")) {
            throw new MapaInvalidoException("template parte de \"" + prefixoVisivel(linha)
                    + "\", mas PASTA_INICIAL e \"" + pastaInicial + "\"");
        }
        // Corta da linha ORIGINAL, nao da normalizada: a normalizacao mapeia caractere a caractere
        // no trecho inicial, entao o tamanho da raiz vale nas duas - e assim os termos preservam a
        // caixa que o suporte escreveu, para o eco do modo de teste ficar fiel.
        String semRaiz = linha.substring(raizNormalizada.length());
        while (semRaiz.startsWith("/") || semRaiz.startsWith("\\")) {
            semRaiz = semRaiz.substring(1);
        }
        if (semRaiz.isBlank()) {
            throw new MapaInvalidoException("template aponta para a propria PASTA_INICIAL, sem"
                    + " nenhum nivel abaixo dela (para varrer so a raiz, use NIVEIS_SUBPASTA=0 sem"
                    + " " + NOME_ARQUIVO + ")");
        }
        return semRaiz;
    }

    private static boolean temPrefixoAbsoluto(String linha) {
        return linha.matches("^[A-Za-z]:[\\\\/].*") || linha.matches("^[\\\\/]{2}.*");
    }

    /** Caixa e tipo de barra sao irrelevantes na comparacao de raiz; o separador final tambem. */
    private static String normalizarCaminho(String caminho) {
        String barrasUniformes = caminho.replace('\\', '/').toLowerCase(Locale.ROOT).trim();
        while (barrasUniformes.endsWith("/")) {
            barrasUniformes = barrasUniformes.substring(0, barrasUniformes.length() - 1);
        }
        return barrasUniformes;
    }

    /** Apenas o comeco do template, para a mensagem de erro nao repetir a linha inteira. */
    private static String prefixoVisivel(String linha) {
        int fim = linha.length();
        for (int i = 3; i < linha.length(); i++) {
            if (linha.charAt(i) == '/' || linha.charAt(i) == '\\') {
                fim = i;
                break;
            }
        }
        return linha.substring(0, fim);
    }
}
