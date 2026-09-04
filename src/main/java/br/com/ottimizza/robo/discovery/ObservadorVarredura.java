package br.com.ottimizza.robo.discovery;

import java.nio.file.Path;

/**
 * Recebe as decisoes tomadas durante a varredura, para o modo de teste
 * ({@code --testar-pastas}) poder explicar <b>por que</b> cada ramo entrou ou foi podado.
 *
 * <p>Existe porque o modo de falha dominante deste robo e o silencio: "roda e nao faz nada" e
 * pior que um erro. Em producao o observador e {@link #NENHUM} e nao custa nada.
 */
public interface ObservadorVarredura {

    ObservadorVarredura NENHUM = new ObservadorVarredura() {
    };

    default void pastaVisitada(Path pasta, int nivel) {
    }

    default void pastaPodada(Path pasta, int nivel, String motivo) {
    }

    default void pastaAlvo(Path pasta, TemplateMapa template) {
    }

    /**
     * A pasta foi aceita e a varredura desceu por ela, mas nenhuma pasta-alvo apareceu abaixo -
     * o template descreve niveis que essa parte da arvore nao tem.
     *
     * <p>E o diagnostico que faltava no Robo 1.0: dois ramos do cliente ({@code IBGE} e
     * {@code SIMPLES}) morriam exatamente assim, em producao, por anos, sem nenhum sinal.
     */
    default void ramoSemAlvo(Path pasta, int nivel, String detalhe) {
    }
}
