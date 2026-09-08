package br.com.ottimizza.robo.discovery;

import java.util.List;

/**
 * Uma linha de {@code mapa-pastas.txt} (secao 6.6 do readme): a descricao de um caminho a partir
 * da {@code PASTA_INICIAL}, segmento por segmento. A pasta no nivel <i>i</i> (1-based) e casada
 * pelo segmento <i>i</i>, e a profundidade do template e onde esta a pasta-alvo daquele caminho.
 */
public final class TemplateMapa {

    private final List<SegmentoMapa> segmentos;
    private final int linha;
    private final String textoOriginal;

    TemplateMapa(List<SegmentoMapa> segmentos, int linha, String textoOriginal) {
        this.segmentos = List.copyOf(segmentos);
        this.linha = linha;
        this.textoOriginal = textoOriginal;
    }

    /** Quantos niveis abaixo da raiz esta a pasta-alvo deste caminho. */
    public int profundidade() {
        return segmentos.size();
    }

    /** O segmento que casa o nivel {@code indice + 1}. */
    public SegmentoMapa segmento(int indice) {
        return segmentos.get(indice);
    }

    /** Numero da linha no arquivo, para mensagens de erro e de poda. */
    public int linha() {
        return linha;
    }

    public String textoOriginal() {
        return textoOriginal;
    }

    public boolean usaTokenDeAno() {
        return segmentos.stream().anyMatch(SegmentoMapa::usaTokenDeAno);
    }

    @Override
    public String toString() {
        return "linha " + linha + ": " + textoOriginal;
    }
}
