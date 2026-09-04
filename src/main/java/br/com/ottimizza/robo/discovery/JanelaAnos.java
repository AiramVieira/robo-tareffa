package br.com.ottimizza.robo.discovery;

/**
 * Janela de anos aceitos: <b>sempre</b> o ano corrente e o anterior (secao 6.4 do readme).
 *
 * <p>Ate a versao anterior a janela era configuravel por {@code VARIACAO_ANOS}. A chave foi
 * removida: na pratica todo cliente quer exatamente estes dois anos, e cada ano extra na janela
 * multiplica a arvore percorrida a cada remapeamento - em pasta de rede isso e a diferenca entre
 * segundos e minutos. Fixar a janela tira do suporte uma decisao que ele nao tinha como acertar
 * e elimina a configuracao invalida "SUBNIVEL_ANO sem VARIACAO_ANOS", que so dava erro silencioso.
 *
 * <p>A mesma janela alimenta as duas features: o filtro de ano do modo {@code NIVEIS_SUBPASTA} e
 * os tokens {@code $ANO}/{@code $MES.ANO} do mapa de pastas (secao 6.6).
 */
public final class JanelaAnos {

    /** Quantos anos para tras do corrente entram na janela. */
    private static final int ANOS_PARA_TRAS = 1;

    private final int anoMinimo;
    private final int anoMaximo;

    private JanelaAnos(int anoMinimo, int anoMaximo) {
        this.anoMinimo = anoMinimo;
        this.anoMaximo = anoMaximo;
    }

    /** A janela fixa em torno do ano informado: {@code [anoAtual - 1, anoAtual]}. */
    public static JanelaAnos criar(int anoAtual) {
        return new JanelaAnos(anoAtual - ANOS_PARA_TRAS, anoAtual);
    }

    public boolean contem(int ano) {
        return ano >= anoMinimo && ano <= anoMaximo;
    }

    public int minimo() {
        return anoMinimo;
    }

    public int maximo() {
        return anoMaximo;
    }

    /**
     * Verifica se algum dos anos aceitos aparece no texto informado. Usado pelo filtro de ano do
     * modo {@code NIVEIS_SUBPASTA}, que checa o caminho acumulado inteiro (secao 6.4).
     */
    public boolean textoContemAnoAceito(String texto) {
        for (int ano = anoMinimo; ano <= anoMaximo; ano++) {
            if (texto.contains(String.valueOf(ano))) {
                return true;
            }
        }
        return false;
    }

    /** Texto curto para o modo de teste, ex.: {@code "2025..2026"}. */
    public String descricao() {
        return anoMinimo == anoMaximo ? String.valueOf(anoMinimo) : anoMinimo + ".." + anoMaximo;
    }
}
