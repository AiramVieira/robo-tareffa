package br.com.ottimizza.robo.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Carrega e valida {@code parametros.txt} a cada ciclo (secoes 3 e 6.1 do readme).
 * Retorna {@link Optional#empty()} quando a configuracao obrigatoria estiver ausente/invalida,
 * o que faz o ciclo inteiro ser ignorado sem tocar em nenhum arquivo.
 */
public final class ParametrosLoader {

    public static final int INTERVALO_VARREDURA_PADRAO_SEGUNDOS = 30;
    public static final int INTERVALO_REMAPEAMENTO_PADRAO_HORAS = 24;

    private ParametrosLoader() {
    }

    public static Optional<Parametros> carregar(Path arquivoParametros) {
        Map<String, String> chaves = ArquivoChaveValor.ler(arquivoParametros);

        String contabilidade = chaves.getOrDefault("CONTABILIDADE", "").trim();
        if (contabilidade.isEmpty()) {
            return Optional.empty();
        }

        String pastaInicial = chaves.getOrDefault("PASTA_INICIAL", "").trim();
        if (pastaInicial.isEmpty() || !pastaInicial.contains(":/")) {
            return Optional.empty();
        }

        String niveisSubpastaTexto = chaves.getOrDefault("NIVEIS_SUBPASTA", "").trim();
        int niveisSubpasta;
        try {
            niveisSubpasta = Integer.parseInt(niveisSubpastaTexto);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        int intervaloVarredura = parseIntOuPadrao(chaves.get("INTERVALO_VARREDURA_SEGUNDOS"), INTERVALO_VARREDURA_PADRAO_SEGUNDOS);
        int intervaloRemapeamento = parseIntOuPadrao(chaves.get("INTERVALO_REMAPEAMENTO_HORAS"), INTERVALO_REMAPEAMENTO_PADRAO_HORAS);

        String pastaEnviar = chaves.getOrDefault("PASTA_ENVIAR", "").trim();
        boolean enviadoDatado = "SIM".equalsIgnoreCase(chaves.getOrDefault("ENVIADO_DATADO", "").trim());
        String customizacao = chaves.getOrDefault("CUSTOMIZACAO", "").trim();
        String subnivelAno = chaves.getOrDefault("SUBNIVEL_ANO", "").trim();
        String variacaoAnos = chaves.getOrDefault("VARIACAO_ANOS", "").trim();

        return Optional.of(new Parametros(
                titleCase(contabilidade),
                pastaInicial,
                niveisSubpasta,
                intervaloVarredura,
                intervaloRemapeamento,
                pastaEnviar,
                enviadoDatado,
                customizacao,
                subnivelAno,
                variacaoAnos));
    }

    private static int parseIntOuPadrao(String valor, int padrao) {
        if (valor == null || valor.trim().isEmpty()) {
            return padrao;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    static String titleCase(String texto) {
        String[] palavras = texto.trim().split("\\s+");
        StringBuilder resultado = new StringBuilder();
        for (int i = 0; i < palavras.length; i++) {
            if (i > 0) {
                resultado.append(' ');
            }
            String palavra = palavras[i];
            if (!palavra.isEmpty()) {
                resultado.append(Character.toUpperCase(palavra.charAt(0)));
                if (palavra.length() > 1) {
                    resultado.append(palavra.substring(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return resultado.toString();
    }
}
