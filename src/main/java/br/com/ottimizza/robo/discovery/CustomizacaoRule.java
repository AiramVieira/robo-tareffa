package br.com.ottimizza.robo.discovery;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Uma regra de excecao de {@code CUSTOMIZACAO} (secao 6.3 do readme), no formato
 * {@code nivel:operadorTermo}, com operadores {@code +}/{@code +!} (aumenta profundidade-alvo
 * para o ramo) e {@code >}/{@code >!} (lista de permissao/bloqueio de pastas).
 */
public final class CustomizacaoRule {

    public enum Operador {
        AUMENTA_PROFUNDIDADE,
        FILTRA
    }

    private final int nivel;
    private final Operador operador;
    private final boolean negado;
    private final String termoNormalizado;

    private CustomizacaoRule(int nivel, Operador operador, boolean negado, String termo) {
        this.nivel = nivel;
        this.operador = operador;
        this.negado = negado;
        this.termoNormalizado = normalizar(termo);
    }

    /**
     * Interpreta a lista completa de regras separadas por {@code ;}.
     * Retorna {@link Optional#empty()} se qualquer regra estiver malformada — nesse caso,
     * toda a estrutura de CUSTOMIZACAO deve ser tratada como invalida (secao 6.3, ultimo paragrafo).
     */
    public static Optional<List<CustomizacaoRule>> parseAll(String customizacao) {
        List<CustomizacaoRule> regras = new ArrayList<>();
        if (customizacao == null || customizacao.isBlank()) {
            return Optional.of(regras);
        }
        for (String parte : customizacao.split(";")) {
            String regraTexto = parte.trim();
            if (regraTexto.isEmpty()) {
                continue;
            }
            Optional<CustomizacaoRule> regra = parseUma(regraTexto);
            if (regra.isEmpty()) {
                return Optional.empty();
            }
            regras.add(regra.get());
        }
        return Optional.of(regras);
    }

    private static Optional<CustomizacaoRule> parseUma(String regraTexto) {
        int posicaoDoisPontos = regraTexto.indexOf(':');
        if (posicaoDoisPontos < 0) {
            return Optional.empty();
        }
        String nivelTexto = regraTexto.substring(0, posicaoDoisPontos).trim();
        String resto = regraTexto.substring(posicaoDoisPontos + 1).trim();

        int nivel;
        try {
            nivel = Integer.parseInt(nivelTexto);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        if (resto.isEmpty()) {
            return Optional.empty();
        }

        char operadorChar = resto.charAt(0);
        Operador operador;
        if (operadorChar == '+') {
            operador = Operador.AUMENTA_PROFUNDIDADE;
        } else if (operadorChar == '>') {
            operador = Operador.FILTRA;
        } else {
            return Optional.empty();
        }

        String restoSemOperador = resto.substring(1);
        boolean negado = restoSemOperador.startsWith("!");
        String termo = negado ? restoSemOperador.substring(1) : restoSemOperador;
        if (termo.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new CustomizacaoRule(nivel, operador, negado, termo));
    }

    public int getNivel() {
        return nivel;
    }

    public Operador getOperador() {
        return operador;
    }

    /**
     * Se a regra "dispara" para o nome de pasta informado (termo contido, nao sensivel a caixa,
     * ignorando caracteres especiais - com a negacao de {@code !} aplicada quando presente).
     */
    public boolean disparaPara(String nomePasta) {
        boolean contem = normalizar(nomePasta).contains(termoNormalizado);
        return negado != contem;
    }

    static String normalizar(String texto) {
        String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcentos.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
