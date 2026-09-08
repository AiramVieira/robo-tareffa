package br.com.ottimizza.robo.discovery;

import br.com.ottimizza.robo.config.Parametros;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Impressao digital da configuracao que determina <b>quais pastas</b> o robo monitora (secao 6.2
 * do readme).
 *
 * <p>Existe porque o cache de pastas-alvo so expirava por tempo: uma regra editada pelo suporte
 * so valia no remapeamento seguinte (ate 24 h). Com o fingerprint no cache, a edicao passa a valer
 * no proximo ciclo de varredura.
 *
 * <p>Entram somente os campos que mudam a lista de pastas. {@code PASTA_ENVIAR} entra porque
 * alimenta os nomes reservados, que mudam o casamento de curingas. O ano corrente entra e conserta
 * um bug latente: um cache gerado em 31/12 continuava valido por ate 24 h com a janela do ano
 * anterior. A janela de anos em si nao entra mais: ela deixou de ser configuravel e
 * passou a ser sempre "ano corrente + anterior", entao {@code anoAtual} ja a determina
 * por inteiro. {@code ENVIADO_DATADO} e os intervalos ficam de fora - nao afetam a descoberta, e
 * inclui-los custaria uma varredura completa a cada edicao inocua.
 *
 * <p>Sem dependencia nova e sem modulo jlink novo: {@code SHA-256} vem do provider {@code SUN},
 * dentro de {@code java.base}.
 */
public final class ConfiguracaoFingerprint {

    /** Permite forcar remapeamento em toda a base instalada se a gramatica do mapa mudar. */
    private static final String VERSAO = "v2";

    private ConfiguracaoFingerprint() {
    }

    /**
     * @param textoMapa conteudo bruto de {@code mapa-pastas.txt}, ou {@code null}/vazio no modo
     *                  {@code NIVEIS_SUBPASTA}. E o texto bruto, e nao a forma interpretada, para
     *                  que o gatilho de remapeamento nunca divirja do parser.
     */
    public static String calcular(Parametros parametros, String textoMapa, int anoAtual) {
        String canonico = VERSAO
                + "\nraiz=" + parametros.getPastaInicial()
                + "\nmodo=" + (textoMapa == null || textoMapa.isBlank() ? "NIVEIS" : "MAPA")
                + "\nniveis=" + parametros.getNiveisSubpasta()
                + "\ncustomizacao=" + parametros.getCustomizacao()
                + "\nsubnivelAno=" + parametros.getSubnivelAno()
                + "\npastaEnviar=" + parametros.getPastaEnviar()
                + "\nanoAtual=" + anoAtual
                + "\nmapa=" + (textoMapa == null ? "" : textoMapa);
        return sha256Hex(canonico);
    }

    private static String sha256Hex(String texto) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 e obrigatorio em toda implementacao da plataforma Java.
            throw new IllegalStateException("SHA-256 indisponivel neste runtime", e);
        }
    }
}
