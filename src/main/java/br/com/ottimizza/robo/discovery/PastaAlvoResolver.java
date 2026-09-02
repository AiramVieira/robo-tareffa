package br.com.ottimizza.robo.discovery;

import br.com.ottimizza.robo.config.Parametros;
import br.com.ottimizza.robo.logging.ErroLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Decide, a cada ciclo, a lista de pastas-alvo: le do cache em disco quando valido, ou
 * dispara um remapeamento completo quando o cache esta ausente/vencido (secao 6.2).
 * Regras de {@code CUSTOMIZACAO} malformadas ou {@code SUBNIVEL_ANO} sem {@code VARIACAO_ANOS}
 * so sao detectadas nesse momento de remapeamento (secao 6.5).
 */
public final class PastaAlvoResolver {

    private final ErroLogger erroLogger;

    public PastaAlvoResolver(ErroLogger erroLogger) {
        this.erroLogger = erroLogger;
    }

    public sealed interface Resultado permits Resultado.Sucesso, Resultado.ConfiguracaoInvalida {
        record Sucesso(List<Path> pastasAlvo) implements Resultado {
        }

        record ConfiguracaoInvalida(String motivo) implements Resultado {
        }
    }

    public Resultado resolver(Parametros parametros) {
        Path raiz = Path.of(parametros.getPastaInicial());

        if (parametros.getNiveisSubpasta() == 0) {
            return new Resultado.Sucesso(List.of(raiz));
        }

        PastaAlvoCache cache = new PastaAlvoCache(raiz);
        Optional<PastaAlvoCache.CacheData> dadosCache = cache.ler();
        if (dadosCache.isPresent() && !cache.expirado(dadosCache.get(), parametros.getIntervaloRemapeamentoHoras())) {
            List<Path> pastas = dadosCache.get().pastas.stream().map(Path::of).collect(Collectors.toList());
            return new Resultado.Sucesso(pastas);
        }

        return remapear(raiz, parametros, cache);
    }

    private Resultado remapear(Path raiz, Parametros parametros, PastaAlvoCache cache) {
        if (!parametros.getSubnivelAno().isEmpty() && parametros.getVariacaoAnos().isEmpty()) {
            return new Resultado.ConfiguracaoInvalida("SUBNIVEL_ANO informado sem VARIACAO_ANOS");
        }

        Optional<List<CustomizacaoRule>> regrasOpt = CustomizacaoRule.parseAll(parametros.getCustomizacao());
        if (regrasOpt.isEmpty()) {
            return new Resultado.ConfiguracaoInvalida("Regra de CUSTOMIZACAO malformada: " + parametros.getCustomizacao());
        }

        FiltroAno filtroAno = FiltroAno.criar(parametros.getSubnivelAno(), parametros.getVariacaoAnos(), Year.now().getValue());
        ArvoreScanner scanner = new ArvoreScanner((mensagem, causa) -> erroLogger.registrar(mensagem, causa));
        List<Path> pastasAlvo = scanner.escanear(raiz, parametros.getNiveisSubpasta(), regrasOpt.get(), filtroAno);

        try {
            cache.salvar(pastasAlvo.stream().map(Path::toString).collect(Collectors.toList()), Instant.now());
        } catch (IOException e) {
            erroLogger.registrar("Falha ao salvar cache de pastas-alvo em " + raiz, e);
        }

        return new Resultado.Sucesso(pastasAlvo);
    }
}
