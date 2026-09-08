package br.com.ottimizza.robo.discovery;

import java.util.List;

/**
 * O conteudo de {@code mapa-pastas.txt} ja interpretado (secao 6.6 do readme), junto com o texto
 * bruto do arquivo.
 *
 * <p>O texto bruto e guardado porque o fingerprint de configuracao (secao 6.2) e calculado sobre
 * ele, e nao sobre a forma interpretada: assim o gatilho de remapeamento nunca pode divergir do
 * parser.
 */
public final class MapaPastas {

    private final List<TemplateMapa> templates;
    private final String textoBruto;

    MapaPastas(List<TemplateMapa> templates, String textoBruto) {
        this.templates = List.copyOf(templates);
        this.textoBruto = textoBruto;
    }

    public List<TemplateMapa> getTemplates() {
        return templates;
    }

    public String getTextoBruto() {
        return textoBruto;
    }

    /** A maior profundidade descrita no mapa - o quanto a varredura pode descer. */
    public int profundidadeMaxima() {
        return templates.stream().mapToInt(TemplateMapa::profundidade).max().orElse(0);
    }

    public boolean usaTokenDeAno() {
        return templates.stream().anyMatch(TemplateMapa::usaTokenDeAno);
    }
}
