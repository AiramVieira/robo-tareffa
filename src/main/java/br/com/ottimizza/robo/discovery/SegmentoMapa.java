package br.com.ottimizza.robo.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Um segmento de um template de {@code mapa-pastas.txt} (secao 6.6 do readme): a regra que decide
 * se o nome de uma pasta, em um nivel especifico, faz parte do caminho descrito.
 *
 * <p>Um segmento e uma lista de alternativas separadas por {@code @}. A pasta casa o segmento se
 * casar ao menos uma alternativa positiva <b>e</b> nao casar nenhuma negativa. Havendo somente
 * alternativas negativas, existe um {@code *} positivo implicito. E a mesma semantica de
 * {@code >}/{@code >!} de {@code CUSTOMIZACAO} (secao 6.3), para nao introduzir um segundo modelo
 * mental.
 *
 * <p>O casamento de texto e por <b>substring da forma normalizada</b>
 * ({@link CustomizacaoRule#normalizar}) e nao por igualdade: o modo de falha da igualdade e o pior
 * possivel aqui — escreve-se {@code Tributos}, a pasta chama-se {@code Tributos Municipais}, e o
 * resultado seria zero pastas-alvo em silencio. O falso-positivo da substring aparece no modo de
 * teste ({@code --testar-pastas}) e tem escape explicito ({@code =TERMO}).
 */
public final class SegmentoMapa {

    /** Nome cru com exatamente um grupo de 4 digitos: {@code 2026}, {@code Ano 2026}. */
    private static final Pattern ANO = Pattern.compile("^\\D*(\\d{4})\\D*$");

    /** Forma normalizada {@code MMAAAA}: {@code 01.2026}, {@code 12-2025}, {@code 1.2026}. */
    private static final Pattern MES_ANO = Pattern.compile("^(0?[1-9]|1[0-2])(\\d{4})$");

    /** Forma normalizada {@code AAAAMM}: {@code 2026.01}. */
    private static final Pattern ANO_MES = Pattern.compile("^(\\d{4})(0?[1-9]|1[0-2])$");

    private static final String TOKENS_CONHECIDOS = "$ANO, $MES.ANO ou $ANO.MES";

    private enum Tipo {
        LITERAL,
        IGUAL,
        QUALQUER,
        TOKEN_ANO,
        TOKEN_MES_ANO,
        TOKEN_ANO_MES
    }

    private record Alternativa(boolean negada, Tipo tipo, String termoNormalizado) {

        boolean casa(String nomePasta, String nomeNormalizado, JanelaAnos anos) {
            return switch (tipo) {
                case LITERAL -> nomeNormalizado.contains(termoNormalizado);
                case IGUAL -> nomeNormalizado.equals(termoNormalizado);
                case QUALQUER -> true;
                case TOKEN_ANO -> anoNaJanela(ANO.matcher(nomePasta), 1, anos);
                case TOKEN_MES_ANO -> anoNaJanela(MES_ANO.matcher(nomeNormalizado), 2, anos);
                case TOKEN_ANO_MES -> anoNaJanela(ANO_MES.matcher(nomeNormalizado), 1, anos);
            };
        }

        private static boolean anoNaJanela(Matcher matcher, int grupoDoAno, JanelaAnos anos) {
            if (!matcher.matches()) {
                return false;
            }
            return anos != null && anos.contem(Integer.parseInt(matcher.group(grupoDoAno)));
        }
    }

    private final String textoOriginal;
    private final List<Alternativa> alternativas;

    private SegmentoMapa(String textoOriginal, List<Alternativa> alternativas) {
        this.textoOriginal = textoOriginal;
        this.alternativas = alternativas;
    }

    public static SegmentoMapa parse(String texto) throws MapaInvalidoException {
        String segmento = texto.trim();
        if (segmento.isEmpty()) {
            throw new MapaInvalidoException("segmento vazio (duas barras seguidas?)");
        }

        // O separador anterior era "|", que e ilegal em nome de pasta no Windows e por isso nao
        // podia colidir com nada. Com "@" a colisao passa a ser possivel (ex.: uma pasta chamada
        // "integracao@escritorio"), entao um mapa antigo com "|" e recusado explicitamente abaixo
        // em vez de virar um literal que nao casa nada - falha alta, nao silenciosa.
        if (segmento.indexOf('|') >= 0) {
            throw new MapaInvalidoException("\"|\" nao e mais o separador de alternativas - use \"@\""
                    + " (ex.: ECD@ECF@IBGE em vez de ECD|ECF|IBGE)");
        }

        List<Alternativa> alternativas = new ArrayList<>();
        // -1 preserva alternativas vazias no fim ("A@"), que precisam virar erro em vez de sumir.
        for (String parte : segmento.split("@", -1)) {
            alternativas.add(parseAlternativa(parte, segmento));
        }
        return new SegmentoMapa(segmento, List.copyOf(alternativas));
    }

    private static Alternativa parseAlternativa(String parte, String segmento) throws MapaInvalidoException {
        String texto = parte.trim();
        if (texto.isEmpty()) {
            throw new MapaInvalidoException("alternativa vazia em \"" + segmento + "\" (arroba sobrando?)");
        }

        boolean negada = false;
        if (texto.startsWith("*!")) {
            negada = true;
            texto = texto.substring(2).trim();
        } else if (texto.startsWith("!")) {
            negada = true;
            texto = texto.substring(1).trim();
        }

        if (texto.isEmpty()) {
            throw new MapaInvalidoException("negacao sem termo em \"" + segmento + "\"");
        }
        if (texto.startsWith("**")) {
            throw new MapaInvalidoException("\"**\" nao e suportado - escreva um * por nivel");
        }
        if ("*".equals(texto)) {
            if (negada) {
                throw new MapaInvalidoException("\"!*\" rejeitaria todas as pastas do nivel");
            }
            return new Alternativa(false, Tipo.QUALQUER, "");
        }
        if (texto.startsWith("=")) {
            String termo = texto.substring(1).trim();
            if (termo.isEmpty()) {
                throw new MapaInvalidoException("\"=\" sem termo em \"" + segmento + "\"");
            }
            return new Alternativa(negada, Tipo.IGUAL, CustomizacaoRule.normalizar(termo));
        }
        if (texto.startsWith("$")) {
            return new Alternativa(negada, tokenDe(texto), "");
        }
        return new Alternativa(negada, Tipo.LITERAL, CustomizacaoRule.normalizar(texto));
    }

    private static Tipo tokenDe(String texto) throws MapaInvalidoException {
        String token = texto.toUpperCase(Locale.ROOT);
        return switch (token) {
            case "$ANO" -> Tipo.TOKEN_ANO;
            case "$MES.ANO" -> Tipo.TOKEN_MES_ANO;
            case "$ANO.MES" -> Tipo.TOKEN_ANO_MES;
            default -> throw new MapaInvalidoException(
                    "token desconhecido \"" + texto + "\" (esperado " + TOKENS_CONHECIDOS + ")");
        };
    }

    /**
     * Se a pasta informada faz parte deste segmento. {@code anos} pode ser {@code null} quando o
     * segmento nao usa nenhum token de ano.
     */
    public boolean aceita(String nomePasta, JanelaAnos anos) {
        String nomeNormalizado = CustomizacaoRule.normalizar(nomePasta);

        boolean temPositiva = false;
        boolean algumaPositivaCasou = false;
        for (Alternativa alternativa : alternativas) {
            boolean casou = alternativa.casa(nomePasta, nomeNormalizado, anos);
            if (alternativa.negada()) {
                if (casou) {
                    return false;
                }
            } else {
                temPositiva = true;
                algumaPositivaCasou |= casou;
            }
        }
        return !temPositiva || algumaPositivaCasou;
    }

    /**
     * Se este segmento pode casar uma pasta de trabalho do proprio robo ({@code ENVIADOS},
     * {@code ERROS}, {@code PASTA_ENVIAR}).
     *
     * <p>So e verdade quando <b>todas</b> as alternativas sao texto explicito ({@code TERMO} ou
     * {@code =TERMO}) e nenhuma e negativa: nesse caso o suporte nomeou a pasta de proposito.
     * Curingas e tokens nunca alcancam essas pastas, senao o mapa se autoenvenena — um alvo raso
     * cria {@code ENVIADOS/} (e {@code ENVIADOS/2026/09}, que casaria {@code $ANO}) e no
     * remapeamento seguinte as pastas do proprio robo entrariam como pastas-alvo.
     */
    public boolean permiteNomeReservado() {
        boolean temPositiva = false;
        for (Alternativa alternativa : alternativas) {
            if (alternativa.negada()) {
                return false;
            }
            if (alternativa.tipo() != Tipo.LITERAL && alternativa.tipo() != Tipo.IGUAL) {
                return false;
            }
            temPositiva = true;
        }
        return temPositiva;
    }

    /** Se algum token de ano ({@code $ANO}, {@code $MES.ANO}, {@code $ANO.MES}) e usado aqui. */
    public boolean usaTokenDeAno() {
        return alternativas.stream().anyMatch(a -> a.tipo() == Tipo.TOKEN_ANO
                || a.tipo() == Tipo.TOKEN_MES_ANO
                || a.tipo() == Tipo.TOKEN_ANO_MES);
    }

    public String textoOriginal() {
        return textoOriginal;
    }

    @Override
    public String toString() {
        return textoOriginal;
    }
}
