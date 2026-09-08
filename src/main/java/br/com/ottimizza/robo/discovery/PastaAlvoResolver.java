package br.com.ottimizza.robo.discovery;

import br.com.ottimizza.robo.config.Parametros;
import br.com.ottimizza.robo.logging.ErroLogger;
import br.com.ottimizza.robo.processamento.CaminhosPasta;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Decide, a cada ciclo, a lista de pastas-alvo: le do cache em disco quando valido, ou dispara um
 * remapeamento completo quando o cache esta ausente, vencido ou foi gerado por outra configuracao
 * (secao 6.2).
 *
 * <p>Dois modos de descoberta, mutuamente exclusivos:
 * <ul>
 *   <li><b>mapa</b> (secao 6.6) - quando existe {@code mapa-pastas.txt} na pasta de instalacao.
 *       Ele passa a ser a unica fonte da descoberta: {@code NIVEIS_SUBPASTA},
 *       {@code CUSTOMIZACAO} e {@code SUBNIVEL_ANO} sao ignorados (com aviso).
 *       Os tokens {@code $ANO}/{@code $MES.ANO} usam a janela fixa de {@link JanelaAnos}.</li>
 *   <li><b>niveis</b> (secoes 6.3/6.4) - o comportamento historico, inalterado.</li>
 * </ul>
 * Combinar as duas linguagens de selecao de pasta produziria uma tabela-verdade que ninguem
 * simula de cabeca, com modo de falha invisivel. Uma chave, uma linguagem.
 */
public final class PastaAlvoResolver {

    private final ErroLogger erroLogger;
    private final Path pastaInstalacao;

    public PastaAlvoResolver(ErroLogger erroLogger, Path pastaInstalacao) {
        this.erroLogger = erroLogger;
        this.pastaInstalacao = pastaInstalacao;
    }

    public sealed interface Resultado permits Resultado.Sucesso, Resultado.ConfiguracaoInvalida {
        record Sucesso(List<Path> pastasAlvo) implements Resultado {
        }

        record ConfiguracaoInvalida(String motivo) implements Resultado {
        }
    }

    /** Descricao do modo em vigor, para a linha de log de cada ciclo. */
    public String descreverModo(Parametros parametros) {
        return descreverModo(pastaInstalacao, parametros);
    }

    public static String descreverModo(Path pastaInstalacao, Parametros parametros) {
        Optional<MapaPastasLoader.Resultado> mapa = carregarMapa(pastaInstalacao, parametros);
        if (mapa.isEmpty()) {
            return "NIVEIS (niveis=" + parametros.getNiveisSubpasta()
                    + " customizacao=" + parametros.getCustomizacao() + ")";
        }
        if (mapa.get() instanceof MapaPastasLoader.Resultado.Carregado carregado) {
            return "MAPA (" + MapaPastasLoader.NOME_ARQUIVO + ", "
                    + carregado.mapa().getTemplates().size() + " templates)";
        }
        return "MAPA (" + MapaPastasLoader.NOME_ARQUIVO + ", invalido)";
    }

    public Resultado resolver(Parametros parametros) {
        Path raiz = Path.of(parametros.getPastaInicial());
        Optional<MapaPastasLoader.Resultado> mapaOpt = carregarMapa(pastaInstalacao, parametros);

        // A decisao de modo vem ANTES do curto-circuito de NIVEIS_SUBPASTA=0: se o suporte soltou
        // o mapa e deixou o parametro em zero, o mapa ainda ganha.
        if (mapaOpt.isEmpty() && parametros.getNiveisSubpasta() == 0) {
            return new Resultado.Sucesso(List.of(raiz));
        }

        String textoMapa = textoDoMapa(mapaOpt);
        String fingerprint = ConfiguracaoFingerprint.calcular(parametros, textoMapa, Year.now().getValue());

        PastaAlvoCache cache = new PastaAlvoCache(raiz);
        Optional<PastaAlvoCache.CacheData> dadosCache = cache.ler();
        if (dadosCache.isPresent()) {
            PastaAlvoCache.CacheData dados = dadosCache.get();
            boolean vencido = cache.expirado(dados, parametros.getIntervaloRemapeamentoHoras());
            boolean mesmaConfig = cache.fingerprintCoincide(dados, fingerprint);
            if (!vencido && mesmaConfig) {
                List<Path> pastas = dados.pastas.stream().map(Path::of).collect(Collectors.toList());
                return new Resultado.Sucesso(pastas);
            }
            System.out.println("Remapeando: " + (mesmaConfig
                    ? "cache vencido (" + parametros.getIntervaloRemapeamentoHoras() + "h)"
                    : "a configuracao de descoberta de pastas mudou"));
        }

        Resultado resultado = calcular(parametros, mapaOpt, ObservadorVarredura.NENHUM,
                erroLogger::registrar);

        if (resultado instanceof Resultado.Sucesso sucesso) {
            avisarSeNenhumaPastaAlvo(sucesso.pastasAlvo(), parametros, raiz);
            try {
                cache.salvar(sucesso.pastasAlvo().stream().map(Path::toString).collect(Collectors.toList()),
                        Instant.now(), fingerprint);
            } catch (IOException e) {
                erroLogger.registrar("Falha ao salvar cache de pastas-alvo em " + raiz, e);
            }
        }
        return resultado;
    }

    /**
     * Resolve as pastas-alvo sem tocar no cache - nem para ler, nem para gravar. Usado pelo modo
     * de teste ({@code --testar-pastas}), que precisa ser estritamente somente-leitura: o arquivo
     * de cache mora dentro da {@code PASTA_INICIAL}, ou seja, na arvore do cliente.
     */
    public static Resultado simular(Path pastaInstalacao, Parametros parametros,
                                     ObservadorVarredura observador,
                                     BiConsumer<String, Exception> avisoLog) {
        Optional<MapaPastasLoader.Resultado> mapaOpt = carregarMapa(pastaInstalacao, parametros);
        if (mapaOpt.isEmpty() && parametros.getNiveisSubpasta() == 0) {
            return new Resultado.Sucesso(List.of(Path.of(parametros.getPastaInicial())));
        }
        return calcular(parametros, mapaOpt, observador, avisoLog);
    }

    public Resultado simular(Parametros parametros, ObservadorVarredura observador,
                              BiConsumer<String, Exception> avisoLog) {
        return simular(pastaInstalacao, parametros, observador, avisoLog);
    }

    private static Resultado calcular(Parametros parametros, Optional<MapaPastasLoader.Resultado> mapaOpt,
                                ObservadorVarredura observador, BiConsumer<String, Exception> avisoLog) {
        Path raiz = Path.of(parametros.getPastaInicial());

        if (mapaOpt.isPresent()) {
            if (mapaOpt.get() instanceof MapaPastasLoader.Resultado.Invalido invalido) {
                return new Resultado.ConfiguracaoInvalida(invalido.motivo());
            }
            MapaPastas mapa = ((MapaPastasLoader.Resultado.Carregado) mapaOpt.get()).mapa();
            avisarChavesIgnoradas(parametros);
            JanelaAnos anos = mapa.usaTokenDeAno()
                    ? JanelaAnos.criar(Year.now().getValue())
                    : null;
            MapaScanner scanner = new MapaScanner(avisoLog, observador);
            return new Resultado.Sucesso(
                    scanner.escanear(raiz, mapa, anos, nomesReservados(parametros)));
        }


        Optional<List<CustomizacaoRule>> regrasOpt = CustomizacaoRule.parseAll(parametros.getCustomizacao());
        if (regrasOpt.isEmpty()) {
            return new Resultado.ConfiguracaoInvalida(
                    "Regra de CUSTOMIZACAO malformada: " + parametros.getCustomizacao());
        }

        FiltroAno filtroAno = FiltroAno.criar(parametros.getSubnivelAno(), Year.now().getValue());
        ArvoreScanner scanner = new ArvoreScanner(avisoLog);
        return new Resultado.Sucesso(
                scanner.escanear(raiz, parametros.getNiveisSubpasta(), regrasOpt.get(), filtroAno));
    }

    public static Optional<MapaPastasLoader.Resultado> carregarMapa(Path pastaInstalacao,
                                                                     Parametros parametros) {
        if (pastaInstalacao == null) {
            return Optional.empty();
        }
        return MapaPastasLoader.carregar(pastaInstalacao.resolve(MapaPastasLoader.NOME_ARQUIVO),
                parametros.getPastaInicial());
    }

    private static String textoDoMapa(Optional<MapaPastasLoader.Resultado> mapaOpt) {
        if (mapaOpt.isPresent() && mapaOpt.get() instanceof MapaPastasLoader.Resultado.Carregado carregado) {
            return carregado.mapa().getTextoBruto();
        }
        return "";
    }

    /**
     * Nomes que curingas e tokens do mapa nunca alcancam: as pastas de trabalho do proprio robo.
     * Sem isso a lista de pastas-alvo se realimenta a cada remapeamento.
     */
    static Set<String> nomesReservados(Parametros parametros) {
        Set<String> reservados = new HashSet<>();
        reservados.add(CustomizacaoRule.normalizar(CaminhosPasta.NOME_ENVIADOS));
        reservados.add(CustomizacaoRule.normalizar(CaminhosPasta.NOME_ERROS));
        if (parametros.getPastaEnviar() != null && !parametros.getPastaEnviar().isBlank()) {
            reservados.add(CustomizacaoRule.normalizar(parametros.getPastaEnviar()));
        }
        return reservados;
    }

    private static void avisarChavesIgnoradas(Parametros parametros) {
        if (parametros.getNiveisSubpasta() != 0) {
            avisarIgnorada("NIVEIS_SUBPASTA=" + parametros.getNiveisSubpasta());
        }
        if (!parametros.getCustomizacao().isEmpty()) {
            avisarIgnorada("CUSTOMIZACAO=" + parametros.getCustomizacao());
        }
        if (!parametros.getSubnivelAno().isEmpty()) {
            avisarIgnorada("SUBNIVEL_ANO=" + parametros.getSubnivelAno());
        }
    }

    private static void avisarIgnorada(String chave) {
        System.out.println("AVISO: " + MapaPastasLoader.NOME_ARQUIVO + " presente - " + chave + " ignorado");
    }

    /**
     * O modo de falha dominante deste robo e o silencio: ele roda 24 h por dia sem fazer nada e
     * ninguem descobre. Este aviso e o que transforma isso em uma linha de diagnostico.
     */
    private void avisarSeNenhumaPastaAlvo(List<Path> pastasAlvo, Parametros parametros, Path raiz) {
        if (!pastasAlvo.isEmpty()) {
            return;
        }
        StringBuilder mensagem = new StringBuilder("Remapeamento encontrou 0 pastas-alvo em " + raiz);
        if (DiagnosticoPastaInicial.pareceUnidadeMapeadaAusente(parametros.getPastaInicial())) {
            mensagem.append(". ").append(DiagnosticoPastaInicial.dicaDeUnidadeMapeada(parametros.getPastaInicial()));
        }
        System.out.println("AVISO: " + mensagem);
        erroLogger.registrar(mensagem.toString(), null);
    }
}
