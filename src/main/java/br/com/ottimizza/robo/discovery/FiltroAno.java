package br.com.ottimizza.robo.discovery;

/**
 * Filtro de ano opcional (secao 6.4 do readme), controlado apenas por {@code SUBNIVEL_ANO}.
 * Quando ativo, so permite continuar a varredura por um ramo se o ano corrente ou o anterior
 * aparecer no caminho acumulado ate aquele ponto - a janela e fixa, ver {@link JanelaAnos}.
 */
public final class FiltroAno {

    private static final FiltroAno INATIVO = new FiltroAno(false, false, -1, null);

    private final boolean ativo;
    private final boolean usaNivelFinal;
    private final int nivelAlvo;
    private final JanelaAnos janela;

    private FiltroAno(boolean ativo, boolean usaNivelFinal, int nivelAlvo, JanelaAnos janela) {
        this.ativo = ativo;
        this.usaNivelFinal = usaNivelFinal;
        this.nivelAlvo = nivelAlvo;
        this.janela = janela;
    }

    public static FiltroAno criar(String subnivelAnoTexto, int anoAtual) {
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

        return new FiltroAno(true, usaNivelFinal, nivelAlvo, JanelaAnos.criar(anoAtual));
    }

    /** A janela de anos aceitos; {@code null} quando o filtro esta inativo. */
    public JanelaAnos getJanela() {
        return janela;
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
        return janela.textoContemAnoAceito(caminhoAcumulado);
    }
}
