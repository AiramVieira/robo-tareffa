package br.com.ottimizza.robo.discovery;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Varredura recursiva completa da arvore de pastas a partir de {@code PASTA_INICIAL}
 * (secao 6 do readme), aplicando as regras de {@code CUSTOMIZACAO} e o filtro de ano.
 */
public final class ArvoreScanner {

    private final BiConsumer<String, Exception> avisoLog;

    public ArvoreScanner() {
        this((mensagem, causa) -> { });
    }

    public ArvoreScanner(BiConsumer<String, Exception> avisoLog) {
        this.avisoLog = avisoLog;
    }

    public List<Path> escanear(Path raiz, int niveisSubpasta, List<CustomizacaoRule> regras, FiltroAno filtroAno) {
        if (niveisSubpasta == 0) {
            return List.of(raiz);
        }
        List<Path> resultado = new ArrayList<>();
        escanearRecursivo(raiz, 0, niveisSubpasta, raiz.toString(), regras, filtroAno, resultado);
        return resultado;
    }

    private void escanearRecursivo(Path pastaAtual, int nivelAtual, int profundidadeAlvoRamo,
                                    String caminhoAcumulado, List<CustomizacaoRule> regras,
                                    FiltroAno filtroAno, List<Path> resultado) {
        if (nivelAtual == profundidadeAlvoRamo) {
            resultado.add(pastaAtual);
            return;
        }

        List<Path> subpastas = SubpastasListagem.listar(pastaAtual, avisoLog);
        int proximoNivel = nivelAtual + 1;

        for (Path filho : subpastas) {
            String nomeFilho = filho.getFileName().toString();

            boolean bloqueadoPorFiltro = false;
            int incrementoProfundidade = 0;

            for (CustomizacaoRule regra : regras) {
                if (regra.getNivel() != proximoNivel) {
                    continue;
                }
                boolean dispara = regra.disparaPara(nomeFilho);
                if (regra.getOperador() == CustomizacaoRule.Operador.FILTRA) {
                    if (!dispara) {
                        bloqueadoPorFiltro = true;
                    }
                } else if (dispara) {
                    incrementoProfundidade += 1;
                }
            }

            if (bloqueadoPorFiltro) {
                continue;
            }

            int novaProfundidadeAlvo = profundidadeAlvoRamo + incrementoProfundidade;
            String novoCaminhoAcumulado = caminhoAcumulado + "/" + nomeFilho;

            if (filtroAno.isAtivo()) {
                int nivelAnoEfetivo = filtroAno.nivelEfetivoPara(novaProfundidadeAlvo);
                if (proximoNivel == nivelAnoEfetivo && !filtroAno.caminhoAceito(novoCaminhoAcumulado)) {
                    continue;
                }
            }

            escanearRecursivo(filho, proximoNivel, novaProfundidadeAlvo, novoCaminhoAcumulado, regras, filtroAno, resultado);
        }
    }

}
