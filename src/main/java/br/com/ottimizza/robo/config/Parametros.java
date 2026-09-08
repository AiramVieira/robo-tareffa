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

    /**
     * Publico para que testes de descoberta de pastas montem uma configuracao direto, sem passar
     * por {@code parametros.txt}: a validacao de {@code PASTA_INICIAL} e especifica do Windows
     * (letra de unidade ou UNC) e amarraria esses testes ao sistema operacional do CI. Este e um
     * value object sem invariante propria - quem valida e {@link ParametrosLoader}.
     */
    public Parametros(String contabilidade, String pastaInicial, int niveisSubpasta,
               int intervaloVarreduraSegundos, int intervaloRemapeamentoHoras,
               String pastaEnviar, boolean enviadoDatado, String customizacao,
               String subnivelAno) {
        this.contabilidade = contabilidade;
        this.pastaInicial = pastaInicial;
        this.niveisSubpasta = niveisSubpasta;
        this.intervaloVarreduraSegundos = intervaloVarreduraSegundos;
        this.intervaloRemapeamentoHoras = intervaloRemapeamentoHoras;
        this.pastaEnviar = pastaEnviar;
        this.enviadoDatado = enviadoDatado;
        this.customizacao = customizacao;
        this.subnivelAno = subnivelAno;
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
}
