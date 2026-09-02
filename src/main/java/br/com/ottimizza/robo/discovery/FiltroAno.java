package br.com.ottimizza.robo.discovery;

/**
 * Filtro de ano opcional (secao 6.4 do readme), controlado por {@code SUBNIVEL_ANO} e
 * {@code VARIACAO_ANOS}. Quando ativo, so permite continuar a varredura por um ramo se o ano
 * atual (dentro da tolerancia configurada) aparecer no caminho acumulado ate aquele ponto.
 */
public final class FiltroAno {

    private static final FiltroAno INATIVO = new FiltroAno(false, false, -1, 0, 0);

    private final boolean ativo;
    private final boolean usaNivelFinal;
    private final int nivelAlvo;
    private final int anoMinimo;
    private final int anoMaximo;

    private FiltroAno(boolean ativo, boolean usaNivelFinal, int nivelAlvo, int anoMinimo, int anoMaximo) {
        this.ativo = ativo;
        this.usaNivelFinal = usaNivelFinal;
        this.nivelAlvo = nivelAlvo;
        this.anoMinimo = anoMinimo;
        this.anoMaximo = anoMaximo;
    }

    public static FiltroAno criar(String subnivelAnoTexto, String variacaoAnosTexto, int anoAtual) {
        if (subnivelAnoTexto == null || subnivelAnoTexto.isBlank()) {
            return INATIVO;
        }

        String subnivelNormalizado = CustomizacaoRule.normalizar(subnivelAnoTexto);
        boolean usaNivelFinal = "0".equals(subnivelAnoTexto.trim()) || subnivelNormalizado.contains("ultim");

        int nivelAlvo = -1;
        if (!usaNivelFinal) {
            try {
                nivelAlvo = Integer.parseInt(subnivelAnoTexto.trim());
            } catch (NumberFormatException e) {
                nivelAlvo = -1;
            }
        }

        int n = extrairDigitos(variacaoAnosTexto);
        boolean temMais = variacaoAnosTexto != null && variacaoAnosTexto.contains("+");
        boolean temMenos = variacaoAnosTexto != null && variacaoAnosTexto.contains("-");

        int anoMinimo;
        int anoMaximo;
        if (temMais && temMenos) {
            anoMinimo = anoAtual - n;
            anoMaximo = anoAtual + n;
        } else if (temMais) {
            anoMinimo = anoAtual;
            anoMaximo = anoAtual + n;
        } else if (temMenos) {
            anoMinimo = anoAtual - n;
            anoMaximo = anoAtual;
        } else {
            anoMinimo = anoAtual;
            anoMaximo = anoAtual;
        }

        return new FiltroAno(true, usaNivelFinal, nivelAlvo, anoMinimo, anoMaximo);
    }

    private static int extrairDigitos(String texto) {
        if (texto == null) {
            return 0;
        }
        String digitos = texto.replaceAll("[^0-9]", "");
        if (digitos.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digitos);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean isAtivo() {
        return ativo;
    }

    public boolean isUsaNivelFinal() {
        return usaNivelFinal;
    }

    public int getNivelAlvo() {
        return nivelAlvo;
    }

    /**
     * Nivel em que o filtro efetivamente se aplica para um ramo especifico, dado a
     * profundidade-alvo daquele ramo (ja ajustada por eventuais regras de CUSTOMIZACAO).
     */
    public int nivelEfetivoPara(int profundidadeAlvoDoRamo) {
        return usaNivelFinal ? profundidadeAlvoDoRamo : nivelAlvo;
    }

    /**
     * Verifica se algum dos anos aceitos aparece no caminho acumulado (secao 6.4: a checagem
     * e contra o caminho completo, nao so o nome da pasta do nivel em questao).
     */
    public boolean caminhoAceito(String caminhoAcumulado) {
        if (!ativo) {
            return true;
        }
        for (int ano = anoMinimo; ano <= anoMaximo; ano++) {
            if (caminhoAcumulado.contains(String.valueOf(ano))) {
                return true;
            }
        }
        return false;
    }
}
