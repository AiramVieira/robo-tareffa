package br.com.ottimizza.robo.mapeador;

import br.com.ottimizza.robo.config.Parametros;
import br.com.ottimizza.robo.discovery.CustomizacaoRule;
import br.com.ottimizza.robo.discovery.DiagnosticoPastaInicial;
import br.com.ottimizza.robo.discovery.JanelaAnos;
import br.com.ottimizza.robo.discovery.ListadorSubpastas;
import br.com.ottimizza.robo.discovery.MapaInvalidoException;
import br.com.ottimizza.robo.discovery.MapaPastas;
import br.com.ottimizza.robo.discovery.MapaPastasLoader;
import br.com.ottimizza.robo.discovery.MapaScanner;
import br.com.ottimizza.robo.discovery.ObservadorVarredura;
import br.com.ottimizza.robo.discovery.PastaAlvoResolver;
import br.com.ottimizza.robo.discovery.SegmentoMapa;
import br.com.ottimizza.robo.discovery.TemplateMapa;
import br.com.ottimizza.robo.processamento.CaminhosPasta;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * O que a tela do mapeador de pastas ({@code mapear.bat}, secao 6.8 do readme) pode pedir. Nada
 * aqui sabe de HTTP - o {@link MapeadorServidor} so traduz JSON -, para a logica ser testada direto.
 *
 * <p>A previa roda o mesmo {@link MapaScanner} do robo sobre a arvore real, e nunca uma
 * reimplementacao das regras em JavaScript: pelo mesmo motivo do {@code --testar-pastas}, o que a
 * tela mostra tem que ser exatamente o que o robo vai fazer. A tela so monta o texto do mapa; quem
 * interpreta e casa as pastas e sempre o motor.
 *
 * <p><b>Invariante:</b> a unica escrita em disco e {@link #salvar}, e ela acontece na pasta de
 * instalacao ({@code mapa-pastas.txt} e a copia do anterior). Na arvore do cliente nada e escrito -
 * nem o cache de pastas-alvo, nem pasta nenhuma.
 */
public final class MapeadorApi {

    /** Onde ficam as versoes anteriores do mapa, uma por salvamento, ao lado do robo.jar. */
    static final String PASTA_COPIAS = "mapa-pastas-anteriores";

    /** O suficiente para marcar a arvore na tela; o total exato vai a parte. */
    static final int LIMITE_ALVOS = 5000;
    static final int LIMITE_NOMES = 500;
    static final int LIMITE_RAMOS = 200;
    static final int LIMITE_ALVOS_ANINHADOS = 50;
    static final int LIMITE_SEM_ACESSO = 50;

    private static final DateTimeFormatter CARIMBO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final Path pastaInstalacao;
    private final Parametros parametros;
    private final Path raiz;
    private final JanelaAnos anos;
    private final Set<String> nomesReservados;
    private final Clock relogio;
    private final ListagemMemorizada listagem = new ListagemMemorizada();
    private final AtomicLong geracaoDaPrevia = new AtomicLong();

    public MapeadorApi(Path pastaInstalacao, Parametros parametros) {
        this(pastaInstalacao, parametros, Year.now().getValue(), Clock.systemDefaultZone());
    }

    MapeadorApi(Path pastaInstalacao, Parametros parametros, int anoAtual, Clock relogio) {
        this.pastaInstalacao = pastaInstalacao;
        this.parametros = parametros;
        this.raiz = Path.of(parametros.getPastaInicial());
        this.anos = JanelaAnos.criar(anoAtual);
        this.nomesReservados = PastaAlvoResolver.nomesReservados(parametros);
        this.relogio = relogio;
    }

    // ------------------------------------------------------------------------------- respostas

    /**
     * @param textoMapa {@code null} quando nao existe {@code mapa-pastas.txt} (robo no modo
     *                  {@code NIVEIS_SUBPASTA})
     * @param erroMapa  {@code null} quando o mapa atual e valido ou nao existe
     */
    public record Estado(String contabilidade, String pastaInicial, String pastaInstalacao,
                         String usuario, boolean podeSerUnidadeMapeada, List<String> pastasDoRobo,
                         String pastaEnviar, int anoMinimo, int anoMaximo, String modoAtual, int niveisSubpasta,
                         String customizacao, String subnivelAno, String textoMapa, String erroMapa) {
    }

    public record Subpasta(String nome, boolean doRobo) {
    }

    public record Subpastas(String caminho, List<Subpasta> pastas, boolean semAcesso) {
    }

    /** @param aceita se a regra do nivel (o segmento enviado) deixa o robo entrar nessa pasta */
    public record NomeNoNivel(String nome, int quantidade, boolean aceita, boolean doRobo) {
    }

    /**
     * @param pastasNoNivelAcima em quantas pastas os nomes foram procurados (uma por empresa, se
     *                           um nivel acima for "todas")
     * @param erro               regra invalida neste nivel ou num nivel acima; {@code null} se ok
     */
    public record NomesNoNivel(int pastasNoNivelAcima, int totalDeNomes, List<NomeNoNivel> nomes,
                               String erro) {
    }

    /** @param linhas linhas do mapa que terminam nesta pasta (mais de uma quando caminhos se sobrepoem) */
    public record Alvo(String caminho, List<Integer> linhas) {
    }

    /** @param esperado o que os caminhos esperavam encontrar no nivel logo abaixo */
    public record RamoSemAlvo(String caminho, List<String> esperado) {
    }

    public record AlvoAninhado(String caminho, String dentroDe) {
    }

    /**
     * @param situacao "ok", "invalida" (o texto nao passa no parser - {@code erro} diz por que) ou
     *                 "superada" (a tela ja pediu uma previa mais nova; esta foi abandonada)
     */
    public record Previa(String situacao, String erro, int linhaDoErro, int totalDeAlvos,
                         List<Alvo> alvos, Map<Integer, Integer> alvosPorLinha,
                         List<RamoSemAlvo> ramosSemAlvo, List<AlvoAninhado> alvosAninhados,
                         List<String> semAcesso, int pastasVisitadas) {

        static Previa invalida(MapaPastasLoader.Resultado.Invalido invalido) {
            return new Previa("invalida", invalido.motivo(), invalido.linha(), 0, List.of(), Map.of(),
                    List.of(), List.of(), List.of(), 0);
        }

        static Previa superada() {
            return new Previa("superada", null, 0, 0, List.of(), Map.of(), List.of(), List.of(),
                    List.of(), 0);
        }
    }

    /** @param copiaAnterior onde ficou a versao anterior, relativo a pasta do robo; {@code null} se nao havia */
    public record Salvamento(boolean salvo, String erro, String copiaAnterior) {
    }

    // -------------------------------------------------------------------------------- operacoes

    public Estado estado() {
        Path arquivoMapa = arquivoMapa();
        String textoMapa = null;
        String erroMapa = null;

        if (Files.isRegularFile(arquivoMapa)) {
            try {
                textoMapa = lerTexto(arquivoMapa);
            } catch (CharacterCodingException e) {
                // Bloco de Notas antigo (Windows 7 / Server 2012) salva em ANSI por padrao, e o robo
                // le UTF-8: o arquivo fica ilegivel para ele. Mostrar o conteudo mesmo assim deixa o
                // analista corrigir salvando pela tela, que grava em UTF-8.
                textoMapa = new String(lerBytes(arquivoMapa), StandardCharsets.ISO_8859_1);
                erroMapa = "O mapa-pastas.txt atual nao esta salvo em UTF-8 (provavelmente foi editado"
                        + " no Bloco de Notas e salvo como ANSI) - o robo nao consegue le-lo. Salvar pelo"
                        + " mapeador corrige isso.";
            } catch (IOException e) {
                erroMapa = "Nao foi possivel ler o mapa-pastas.txt atual: " + e.getMessage();
            }
            if (erroMapa == null && MapaPastasLoader.interpretar(textoMapa, parametros.getPastaInicial())
                    instanceof MapaPastasLoader.Resultado.Invalido invalido) {
                erroMapa = invalido.motivo();
            }
        }

        List<String> pastasDoRobo = new ArrayList<>(List.of(CaminhosPasta.NOME_ENVIADOS, CaminhosPasta.NOME_ERROS));
        if (!parametros.getPastaEnviar().isBlank()) {
            pastasDoRobo.add(parametros.getPastaEnviar());
        }

        return new Estado(parametros.getContabilidade(), parametros.getPastaInicial(),
                pastaInstalacao.toString(), System.getProperty("user.name", "?"),
                DiagnosticoPastaInicial.podeSerUnidadeMapeada(parametros.getPastaInicial()),
                pastasDoRobo, parametros.getPastaEnviar(), anos.minimo(), anos.maximo(),
                Files.isRegularFile(arquivoMapa) ? "MAPA" : "NIVEIS", parametros.getNiveisSubpasta(),
                parametros.getCustomizacao(), parametros.getSubnivelAno(), textoMapa, erroMapa);
    }

    /** Subpastas de uma pasta da arvore do cliente, para a tela desenhar o explorador. */
    public Subpastas subpastas(String caminhoRelativo) {
        Path pasta = resolverRelativo(caminhoRelativo);
        List<Subpasta> pastas = new ArrayList<>();
        for (Path filho : listagem.listar(pasta)) {
            String nome = filho.getFileName().toString();
            pastas.add(new Subpasta(nome, ehDoRobo(nome)));
        }
        return new Subpastas(relativo(pasta), pastas, listagem.semAcesso(pasta));
    }

    /**
     * Os nomes de pasta que existem no nivel {@code prefixo.size() + 1}, somando todos os ramos que
     * os niveis de cima aceitam, e se a regra {@code segmento} deixaria o robo entrar em cada um.
     *
     * <p>E o que tira o analista do escuro: com "todas as empresas" no nivel de cima, ele ve de
     * uma vez que umas empresas chamam a pasta de {@code GUIAS} e outras de {@code Guias Pagas},
     * e quais delas a regra que ele escolheu alcanca.
     *
     * @param prefixo  as regras dos niveis acima, uma por nivel, na sintaxe do mapa
     * @param segmento a regra deste nivel; vazio ou {@code null} quando ainda nao ha regra
     */
    public NomesNoNivel nomesNoNivel(List<String> prefixo, String segmento) {
        String erroPrefixo = validarPrefixo(prefixo);
        if (erroPrefixo != null) {
            return new NomesNoNivel(0, 0, List.of(), erroPrefixo);
        }

        List<Path> pastasAcima;
        if (prefixo.isEmpty()) {
            pastasAcima = List.of(raiz);
        } else {
            MapaPastasLoader.Resultado resultado = MapaPastasLoader.interpretar(String.join("/", prefixo), null);
            if (resultado instanceof MapaPastasLoader.Resultado.Invalido invalido) {
                return new NomesNoNivel(0, 0, List.of(),
                        "regra invalida num nivel acima: " + semPrefixoDeLinha(invalido));
            }
            MapaPastas mapa = ((MapaPastasLoader.Resultado.Carregado) resultado).mapa();
            pastasAcima = new MapaScanner(ObservadorVarredura.NENHUM, listagem)
                    .escanear(raiz, mapa, anos, nomesReservados);
        }

        SegmentoMapa regra = null;
        String erroSegmento = null;
        if (segmento != null && !segmento.isBlank()) {
            try {
                regra = SegmentoMapa.parse(segmento);
            } catch (MapaInvalidoException e) {
                erroSegmento = e.getMessage();
            }
        }

        // Windows nao diferencia maiusculas em nome de pasta, entao GUIAS e Guias sao o mesmo
        // nome para quem esta olhando; a contagem soma os dois.
        Map<String, Integer> contagem = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Path pasta : pastasAcima) {
            for (Path filho : listagem.listar(pasta)) {
                contagem.merge(filho.getFileName().toString(), 1, Integer::sum);
            }
        }

        List<NomeNoNivel> nomes = new ArrayList<>();
        for (Map.Entry<String, Integer> entrada : contagem.entrySet()) {
            if (nomes.size() >= LIMITE_NOMES) {
                break;
            }
            String nome = entrada.getKey();
            boolean doRobo = ehDoRobo(nome);
            boolean aceita = regra != null && regra.aceita(nome, anos)
                    && (!doRobo || regra.permiteNomeReservado());
            nomes.add(new NomeNoNivel(nome, entrada.getValue(), aceita, doRobo));
        }
        return new NomesNoNivel(pastasAcima.size(), contagem.size(), nomes, erroSegmento);
    }

    /**
     * Roda o mapa {@code texto} sobre a arvore real, como o robo faria, sem gravar nada.
     *
     * <p>Cada chamada abandona a anterior que ainda estiver rodando: a tela pede uma previa nova a
     * cada ajuste, e numa arvore de rede grande nao faz sentido terminar de varrer uma regra que o
     * analista ja trocou.
     */
    public Previa previa(String texto) {
        long minhaGeracao = geracaoDaPrevia.incrementAndGet();

        MapaPastasLoader.Resultado resultado = MapaPastasLoader.interpretar(
                texto == null ? "" : texto, parametros.getPastaInicial());
        if (resultado instanceof MapaPastasLoader.Resultado.Invalido invalido) {
            return Previa.invalida(invalido);
        }
        MapaPastas mapa = ((MapaPastasLoader.Resultado.Carregado) resultado).mapa();

        Set<String> semAcesso = new LinkedHashSet<>();
        ListadorSubpastas listador = pasta -> {
            if (geracaoDaPrevia.get() != minhaGeracao) {
                throw new PreviaSuperada();
            }
            List<Path> filhos = listagem.listar(pasta);
            if (listagem.semAcesso(pasta) && semAcesso.size() < LIMITE_SEM_ACESSO) {
                semAcesso.add(relativo(pasta));
            }
            return filhos;
        };

        Coletor coletor = new Coletor();
        List<Path> pastasAlvo;
        try {
            pastasAlvo = new MapaScanner(coletor, listador).escanear(raiz, mapa, anos, nomesReservados);
        } catch (PreviaSuperada e) {
            return Previa.superada();
        }

        Map<Integer, Integer> alvosPorLinha = new TreeMap<>();
        List<Alvo> alvos = new ArrayList<>();
        for (Path pasta : pastasAlvo) {
            List<Integer> linhas = linhasQueTerminamEm(pasta, coletor.linhaQueAchou.get(pasta), mapa);
            linhas.forEach(linha -> alvosPorLinha.merge(linha, 1, Integer::sum));
            if (alvos.size() < LIMITE_ALVOS) {
                alvos.add(new Alvo(relativo(pasta), linhas));
            }
        }

        return new Previa("ok", null, 0, pastasAlvo.size(), alvos, alvosPorLinha,
                ramosSemAlvo(coletor.ramosSemAlvo, mapa), alvosAninhados(pastasAlvo),
                List.copyOf(semAcesso), coletor.visitadas);
    }

    /**
     * Grava {@code texto} como {@code mapa-pastas.txt}, somente se ele passar no mesmo parser que o
     * robo usa - um mapa invalido faria o robo parar de enviar no ciclo seguinte. A versao anterior
     * fica guardada em {@value #PASTA_COPIAS}.
     *
     * <p>A troca e atomica (arquivo temporario + rename): o servico pode estar lendo o mapa nesse
     * mesmo instante, e nao pode ver um arquivo pela metade.
     */
    public synchronized Salvamento salvar(String texto) {
        String conteudo = texto == null ? "" : texto;
        if (MapaPastasLoader.interpretar(conteudo, parametros.getPastaInicial())
                instanceof MapaPastasLoader.Resultado.Invalido invalido) {
            return new Salvamento(false, invalido.motivo(), null);
        }

        Path arquivo = arquivoMapa();
        byte[] bytes = conteudo.getBytes(StandardCharsets.UTF_8);
        String copiaAnterior = null;
        Path temporario = null;
        try {
            if (Files.isRegularFile(arquivo) && !Arrays.equals(Files.readAllBytes(arquivo), bytes)) {
                Path pastaCopias = pastaInstalacao.resolve(PASTA_COPIAS);
                Files.createDirectories(pastaCopias);
                Path copia = pastaCopias.resolve("mapa-pastas_" + LocalDateTime.now(relogio).format(CARIMBO) + ".txt");
                Files.copy(arquivo, copia, StandardCopyOption.REPLACE_EXISTING);
                copiaAnterior = pastaInstalacao.relativize(copia).toString();
            }

            temporario = Files.createTempFile(pastaInstalacao, "mapa-pastas", ".tmp");
            Files.write(temporario, bytes);
            try {
                Files.move(temporario, arquivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING);
            }
            temporario = null;
        } catch (IOException e) {
            return new Salvamento(false, "Nao foi possivel gravar " + arquivo + ": " + e.getMessage(), null);
        } finally {
            if (temporario != null) {
                try {
                    Files.deleteIfExists(temporario);
                } catch (IOException ignorada) {
                    // Sobra um .tmp na pasta do robo; nao tem o nome do mapa, entao nao muda nada.
                }
            }
        }
        return new Salvamento(true, null, copiaAnterior);
    }

    /** Esquece as listagens guardadas, para a tela enxergar pastas criadas durante a sessao. */
    public void esquecerPastas() {
        listagem.esquecer();
    }

    // ------------------------------------------------------------------------------- auxiliares

    private Path arquivoMapa() {
        return pastaInstalacao.resolve(MapaPastasLoader.NOME_ARQUIVO);
    }

    private boolean ehDoRobo(String nomePasta) {
        return nomesReservados.contains(CustomizacaoRule.normalizar(nomePasta));
    }

    /**
     * Converte o caminho que a tela manda (relativo a {@code PASTA_INICIAL}, com {@code /}) em
     * {@link Path}, recusando qualquer coisa que saia da arvore do cliente.
     */
    Path resolverRelativo(String caminhoRelativo) {
        Path pasta = raiz;
        if (caminhoRelativo == null || caminhoRelativo.isEmpty()) {
            return pasta;
        }
        for (String parte : caminhoRelativo.split("/", -1)) {
            if (parte.isBlank() || parte.equals(".") || parte.equals("..")
                    || parte.indexOf('\\') >= 0 || parte.indexOf(':') >= 0) {
                throw new IllegalArgumentException("caminho invalido: " + caminhoRelativo);
            }
            pasta = pasta.resolve(parte);
        }
        return pasta;
    }

    private String relativo(Path pasta) {
        return raiz.relativize(pasta).toString().replace('\\', '/');
    }

    /** Cada regra do prefixo tem que ser exatamente um nivel; o parser do mapa nao pode reparti-la. */
    private static String validarPrefixo(List<String> prefixo) {
        for (int i = 0; i < prefixo.size(); i++) {
            String regra = prefixo.get(i);
            if (regra == null || regra.isBlank()) {
                return "o nivel " + (i + 1) + " ainda nao tem regra";
            }
            if (regra.indexOf('/') >= 0 || regra.indexOf('\\') >= 0) {
                return "a regra do nivel " + (i + 1) + " tem uma barra - cada nivel e uma regra so";
            }
            if (i == 0 && regra.trim().startsWith("#")) {
                return "a regra do nivel 1 comeca com # - no arquivo isso viraria um comentario";
            }
        }
        return null;
    }

    private static String semPrefixoDeLinha(MapaPastasLoader.Resultado.Invalido invalido) {
        String prefixo = MapaPastasLoader.NOME_ARQUIVO + " linha " + invalido.linha() + ": ";
        return invalido.motivo().startsWith(prefixo)
                ? invalido.motivo().substring(prefixo.length())
                : invalido.motivo();
    }

    /**
     * Todas as linhas do mapa que terminam nesta pasta. O scanner so conta a primeira (a pasta e
     * alvo uma vez so), mas para a tela dizer quantas pastas <i>cada</i> caminho alcanca, um
     * caminho cujas pastas ja sao todas cobertas por outro nao pode aparecer com zero.
     */
    private List<Integer> linhasQueTerminamEm(Path pasta, Integer linhaQueAchou, MapaPastas mapa) {
        List<String> nomes = nomesDoCaminho(pasta);
        Set<Integer> linhas = new LinkedHashSet<>();
        if (linhaQueAchou != null) {
            linhas.add(linhaQueAchou);
        }
        for (TemplateMapa template : mapa.getTemplates()) {
            if (template.profundidade() == nomes.size() && aceitaOsNiveis(template, nomes, nomes.size())) {
                linhas.add(template.linha());
            }
        }
        return new ArrayList<>(linhas);
    }

    private boolean aceitaOsNiveis(TemplateMapa template, List<String> nomes, int niveis) {
        for (int i = 0; i < niveis; i++) {
            SegmentoMapa segmento = template.segmento(i);
            String nome = nomes.get(i);
            if (!segmento.aceita(nome, anos) || (ehDoRobo(nome) && !segmento.permiteNomeReservado())) {
                return false;
            }
        }
        return true;
    }

    private List<String> nomesDoCaminho(Path pasta) {
        List<String> nomes = new ArrayList<>();
        for (Path parte : raiz.relativize(pasta)) {
            nomes.add(parte.toString());
        }
        return nomes;
    }

    /**
     * So os ramos mais profundos, como no {@code --testar-pastas}: a recursao reporta o filho
     * antes do pai, e o pai so ficou sem alvo por causa do filho ja listado.
     */
    private List<RamoSemAlvo> ramosSemAlvo(List<Path> ramos, MapaPastas mapa) {
        // Subir pelos pais de cada ramo e linear; comparar ramo com ramo seria quadratico, e com
        // "todas as empresas" num nivel acima podem ser milhares de ramos.
        Set<Path> comDescendenteListado = new HashSet<>();
        for (Path ramo : ramos) {
            for (Path acima = ramo.getParent(); acima != null && !acima.equals(raiz); acima = acima.getParent()) {
                comDescendenteListado.add(acima);
            }
        }

        List<RamoSemAlvo> resultado = new ArrayList<>();
        for (Path ramo : ramos) {
            if (resultado.size() >= LIMITE_RAMOS) {
                break;
            }
            if (comDescendenteListado.contains(ramo)) {
                continue;
            }
            List<String> nomes = nomesDoCaminho(ramo);
            Set<String> esperado = new LinkedHashSet<>();
            for (TemplateMapa template : mapa.getTemplates()) {
                if (template.profundidade() > nomes.size() && aceitaOsNiveis(template, nomes, nomes.size())) {
                    esperado.add(template.segmento(nomes.size()).textoOriginal());
                }
            }
            resultado.add(new RamoSemAlvo(relativo(ramo), List.copyOf(esperado)));
        }
        return resultado;
    }

    private List<AlvoAninhado> alvosAninhados(List<Path> pastasAlvo) {
        Set<Path> conjunto = new HashSet<>(pastasAlvo);
        List<AlvoAninhado> aninhados = new ArrayList<>();
        for (Path pasta : pastasAlvo) {
            for (Path acima = pasta.getParent(); acima != null && acima.startsWith(raiz) && !acima.equals(raiz);
                 acima = acima.getParent()) {
                if (conjunto.contains(acima)) {
                    aninhados.add(new AlvoAninhado(relativo(pasta), relativo(acima)));
                    break;
                }
            }
            if (aninhados.size() >= LIMITE_ALVOS_ANINHADOS) {
                break;
            }
        }
        return aninhados;
    }

    private static String lerTexto(Path arquivo) throws IOException {
        // O decoder padrao do Files.readString ja recusa bytes invalidos, mas sem distinguir isso
        // de um erro de leitura - aqui a distincao importa, porque o caso ANSI tem conserto.
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(Files.readAllBytes(arquivo))).toString();
    }

    private static byte[] lerBytes(Path arquivo) {
        try {
            return Files.readAllBytes(arquivo);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** Lancada de dentro da listagem para abandonar uma varredura que a tela ja trocou por outra. */
    private static final class PreviaSuperada extends RuntimeException {
        PreviaSuperada() {
            super(null, null, false, false);
        }
    }

    private static final class Coletor implements ObservadorVarredura {
        final Map<Path, Integer> linhaQueAchou = new HashMap<>();
        final List<Path> ramosSemAlvo = new ArrayList<>();
        int visitadas;

        @Override
        public void pastaVisitada(Path pasta, int nivel) {
            visitadas++;
        }

        @Override
        public void pastaAlvo(Path pasta, TemplateMapa template) {
            linhaQueAchou.put(pasta, template.linha());
        }

        @Override
        public void ramoSemAlvo(Path pasta, int nivel, String detalhe) {
            ramosSemAlvo.add(pasta);
        }
    }
}
