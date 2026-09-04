package br.com.ottimizza.robo.discovery;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Varredura em modo mapa (secao 6.6 do readme): desce a arvore mantendo, em cada no, o conjunto de
 * templates de {@code mapa-pastas.txt} ainda viaveis naquele caminho. Uma pasta e visitada se
 * algum template a aceita naquele segmento, e e <b>pasta-alvo</b> se algum template termina ali.
 *
 * <p>Como nenhum segmento casa multiplos niveis (o {@code **} e rejeitado no parse), vale a
 * invariante <i>indice do segmento == nivel atual</i>. Duas consequencias importantes: o estado da
 * recursao e apenas {@code (pasta, nivel, templates vivos)}, e a profundidade da varredura fica
 * limitada pelo maior template - o que torna a recursao imune a junctions e links ciclicos, que
 * {@code Files.list} seguiria.
 *
 * <p>Quando dois templates terminam em niveis diferentes no mesmo ramo, <b>os dois</b> sao
 * pasta-alvo. Isso preserva monotonicidade: acrescentar uma linha ao mapa so pode adicionar
 * pastas-alvo, nunca remover - o suporte amplia a cobertura sem risco de quebrar o que ja
 * funcionava.
 */
public final class MapaScanner {

    private final BiConsumer<String, Exception> avisoLog;
    private final ObservadorVarredura observador;

    public MapaScanner() {
        this((mensagem, causa) -> { }, ObservadorVarredura.NENHUM);
    }

    public MapaScanner(BiConsumer<String, Exception> avisoLog, ObservadorVarredura observador) {
        this.avisoLog = avisoLog;
        this.observador = observador;
    }

    /**
     * @param anos            janela para os tokens {@code $ANO}/{@code $MES.ANO}; pode ser
     *                        {@code null} se o mapa nao usa nenhum
     * @param nomesReservados nomes normalizados das pastas de trabalho do robo, que curingas e
     *                        tokens nunca alcancam (ver {@link SegmentoMapa#permiteNomeReservado()})
     */
    public List<Path> escanear(Path raiz, MapaPastas mapa, JanelaAnos anos, Set<String> nomesReservados) {
        Set<Path> alvos = new LinkedHashSet<>();
        observador.pastaVisitada(raiz, 0);
        escanearRecursivo(raiz, 0, mapa.getTemplates(), anos, nomesReservados, alvos);
        return new ArrayList<>(alvos);
    }

    private void escanearRecursivo(Path pastaAtual, int nivelAtual, List<TemplateMapa> vivos,
                                    JanelaAnos anos, Set<String> nomesReservados, Set<Path> alvos) {
        for (Path filho : SubpastasListagem.listar(pastaAtual, avisoLog)) {
            String nomeFilho = filho.getFileName().toString();
            int nivelFilho = nivelAtual + 1;

            List<TemplateMapa> sobreviventes = vivos.stream()
                    .filter(t -> t.segmento(nivelAtual).aceita(nomeFilho, anos))
                    .collect(Collectors.toList());

            if (sobreviventes.isEmpty()) {
                observador.pastaPodada(filho, nivelFilho, motivoDaPoda(vivos, nivelAtual, nomeFilho, anos));
                continue;
            }

            if (nomesReservados.contains(CustomizacaoRule.normalizar(nomeFilho))
                    && sobreviventes.stream().noneMatch(t -> t.segmento(nivelAtual).permiteNomeReservado())) {
                observador.pastaPodada(filho, nivelFilho,
                        "pasta de trabalho do robo (" + nomeFilho + ") - so um segmento com o nome"
                        + " escrito por extenso alcanca essa pasta");
                continue;
            }

            observador.pastaVisitada(filho, nivelFilho);

            List<TemplateMapa> continuam = new ArrayList<>();
            for (TemplateMapa template : sobreviventes) {
                if (template.profundidade() == nivelFilho) {
                    if (alvos.add(filho)) {
                        observador.pastaAlvo(filho, template);
                    }
                } else {
                    continuam.add(template);
                }
            }

            if (!continuam.isEmpty()) {
                int alvosAntes = alvos.size();
                escanearRecursivo(filho, nivelFilho, continuam, anos, nomesReservados, alvos);
                if (alvos.size() == alvosAntes) {
                    observador.ramoSemAlvo(filho, nivelFilho, esperadoNoProximoNivel(continuam, nivelFilho));
                }
            }
        }
    }

    /** Motivo legivel da poda, citando o que os templates daquele nivel esperavam encontrar. */
    private static String motivoDaPoda(List<TemplateMapa> vivos, int nivelAtual, String nomeFilho,
                                        JanelaAnos anos) {
        boolean envolveAno = vivos.stream().anyMatch(t -> t.segmento(nivelAtual).usaTokenDeAno());
        String janela = (anos != null && envolveAno) ? " (anos aceitos: " + anos.descricao() + ")" : "";
        return "nenhum template aceita \"" + nomeFilho + "\" neste nivel - esperado "
                + esperadoNoNivel(vivos, nivelAtual) + janela;
    }

    private static String esperadoNoNivel(List<TemplateMapa> templates, int indiceDoSegmento) {
        return templates.stream()
                .map(t -> t.segmento(indiceDoSegmento).textoOriginal())
                .distinct()
                .collect(Collectors.joining(" ou "));
    }

    private static String esperadoNoProximoNivel(List<TemplateMapa> continuam, int nivelFilho) {
        return "os templates esperavam encontrar " + esperadoNoNivel(continuam, nivelFilho)
                + " no nivel " + (nivelFilho + 1);
    }
}
