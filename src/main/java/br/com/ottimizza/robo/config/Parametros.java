package br.com.ottimizza.robo.config;

/**
 * Configuracao de um ciclo, carregada a partir de {@code parametros.txt} (secao 3 do readme).
 * Imutavel: cada ciclo recarrega e cria uma nova instancia.
 */
public final class Parametros {

    private final String contabilidade;
    private final String pastaInicial;
    private final int niveisSubpasta;
    private final int intervaloVarreduraSegundos;
    private final int intervaloRemapeamentoHoras;
    private final String pastaEnviar;
    private final boolean enviadoDatado;
    private final String customizacao;
    private final String subnivelAno;
    private final String variacaoAnos;

    Parametros(String contabilidade, String pastaInicial, int niveisSubpasta,
               int intervaloVarreduraSegundos, int intervaloRemapeamentoHoras,
               String pastaEnviar, boolean enviadoDatado, String customizacao,
               String subnivelAno, String variacaoAnos) {
        this.contabilidade = contabilidade;
        this.pastaInicial = pastaInicial;
        this.niveisSubpasta = niveisSubpasta;
        this.intervaloVarreduraSegundos = intervaloVarreduraSegundos;
        this.intervaloRemapeamentoHoras = intervaloRemapeamentoHoras;
        this.pastaEnviar = pastaEnviar;
        this.enviadoDatado = enviadoDatado;
        this.customizacao = customizacao;
        this.subnivelAno = subnivelAno;
        this.variacaoAnos = variacaoAnos;
    }

    public String getContabilidade() {
        return contabilidade;
    }

    public String getPastaInicial() {
        return pastaInicial;
    }

    public int getNiveisSubpasta() {
        return niveisSubpasta;
    }

    public int getIntervaloVarreduraSegundos() {
        return intervaloVarreduraSegundos;
    }

    public int getIntervaloRemapeamentoHoras() {
        return intervaloRemapeamentoHoras;
    }

    public String getPastaEnviar() {
        return pastaEnviar;
    }

    public boolean isEnviadoDatado() {
        return enviadoDatado;
    }

    public String getCustomizacao() {
        return customizacao;
    }

    public String getSubnivelAno() {
        return subnivelAno;
    }

    public String getVariacaoAnos() {
        return variacaoAnos;
    }
}
