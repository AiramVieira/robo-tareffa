package br.com.ottimizza.robo.discovery;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Diagnostico da {@code PASTA_INICIAL}, para a falha mais comum e mais silenciosa da instalacao:
 * um caminho de <b>unidade mapeada</b> ({@code W:/...}).
 *
 * <p>Unidades mapeadas sao por sessao de usuario e <b>nao existem para um servico do Windows</b>,
 * mesmo rodando sob uma conta de dominio. O resultado e {@code Files.list} lancando
 * {@code NoSuchFileException}, zero pastas-alvo, e o robo rodando para sempre sem enviar nada. O
 * {@code install.ps1} ja trata isso para a pasta de instalacao ({@code Resolver-CaminhoServico}),
 * mas nunca para a {@code PASTA_INICIAL}.
 *
 * <p>Este diagnostico apenas <b>avisa</b>. Converter {@code W:} para UNC automaticamente exigiria
 * WMI, seria uma mutacao surpreendente da configuracao, e poderia estar errada - o mapeamento pode
 * simplesmente nao existir sob a conta do servico. O script sugere; o humano decide.
 */
public final class DiagnosticoPastaInicial {

    private DiagnosticoPastaInicial() {
    }

    /** Se o valor comeca com uma letra de unidade, ex.: {@code W:/Clientes} ou {@code W:\Clientes}. */
    public static boolean ehLetraDeUnidade(String pastaInicial) {
        return pastaInicial != null && pastaInicial.matches("^[A-Za-z]:[\\\\/].*");
    }

    /**
     * Se vale avisar sobre unidade mapeada quando o caminho <b>existe</b>.
     *
     * <p>O Java nao distingue unidade mapeada de disco local ({@code FileStore} nao expoe
     * {@code DRIVE_REMOTE}), entao aqui a heuristica e simples e conservadora: a unidade do sistema
     * nunca e um mapeamento de rede, e avisar sobre ela seria puro ruido num relatorio que o
     * suporte precisa conseguir ler. Para as outras letras o aviso e um alerta ("pode nao
     * enxergar"), nao uma afirmacao - quem resolve de verdade e o {@code testar.ps1}, que consulta
     * o Windows.
     */
    public static boolean podeSerUnidadeMapeada(String pastaInicial) {
        if (!ehLetraDeUnidade(pastaInicial)) {
            return false;
        }
        String unidadeDoSistema = System.getenv("SystemDrive");
        String unidade = letraDaUnidade(pastaInicial);
        return !unidade.equalsIgnoreCase(unidadeDoSistema == null ? "C:" : unidadeDoSistema);
    }

    public static boolean existe(String pastaInicial) {
        try {
            return pastaInicial != null && Files.isDirectory(Path.of(pastaInicial));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * Letra de unidade que nao existe nem para o usuario atual - o caso mais grave, e onde a dica
     * de UNC vale para qualquer letra (inclusive a do sistema, porque ai o caminho esta errado de
     * todo jeito).
     */
    public static boolean pareceUnidadeMapeadaAusente(String pastaInicial) {
        return ehLetraDeUnidade(pastaInicial) && !existe(pastaInicial);
    }

    public static String letraDaUnidade(String pastaInicial) {
        return pastaInicial.substring(0, 2);
    }

    public static String dicaDeUnidadeMapeada(String pastaInicial) {
        return "PASTA_INICIAL=" + pastaInicial + " nao foi encontrada. Unidades mapeadas ("
                + letraDaUnidade(pastaInicial) + ") nao sao visiveis para um servico do Windows"
                + " - use o caminho UNC (\\\\servidor\\pasta).";
    }

    /**
     * Aviso para o caso ambiguo: o caminho existe para quem esta rodando o teste agora, mas o
     * servico roda sob outra conta e pode nao enxergar a mesma unidade.
     */
    public static String avisoDeUnidadeMapeadaPresente(String pastaInicial) {
        return "PASTA_INICIAL=" + pastaInicial + " foi encontrada AGORA, rodando como "
                + System.getProperty("user.name", "?") + ". O servico roda sob outra conta e pode nao"
                + " enxergar " + letraDaUnidade(pastaInicial) + " - prefira o caminho UNC.";
    }
}
