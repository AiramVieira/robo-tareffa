package br.com.ottimizza.robo.mapeador;

import br.com.ottimizza.robo.config.Parametros;
import br.com.ottimizza.robo.config.ParametrosLoader;
import br.com.ottimizza.robo.diagnostico.TestarPastas;
import br.com.ottimizza.robo.discovery.DiagnosticoPastaInicial;

import java.awt.Desktop;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Modo mapeador ({@code robo.jar --mapear}, via {@code mapear.bat}): abre no navegador uma tela onde
 * o analista monta o {@code mapa-pastas.txt} clicando na arvore real do cliente, em vez de escrever
 * a sintaxe do mapa a mao (secao 6.8 do readme).
 *
 * <p>Existe porque o mapa, embora seja so uma lista de caminhos, pede que se pense em segmentos,
 * curingas e tokens, e tem armadilhas que so a documentacao ensina ("dentro do segmento E, entre
 * linhas OU"). Na tela o analista aponta uma pasta de exemplo e escolhe, nivel a nivel, em
 * portugues, o que vale para as pastas parecidas - e ve na hora, com o motor do robo, quais pastas
 * seriam monitoradas.
 *
 * <p>Roda como o usuario logado, sem elevacao, pelo mesmo motivo do {@code testar.bat}: e esse
 * usuario que o analista enxerga na sessao remota. Toda a saida do console e ASCII, como a do
 * {@link TestarPastas}.
 */
public final class Mapeador {

    public static final int OK = 0;
    public static final int ERRO_INESPERADO = 1;
    public static final int CONFIGURACAO_INVALIDA = 2;

    private Mapeador() {
    }

    /**
     * @param abrirNavegador {@code false} so imprime o endereco: para quando o navegador padrao da
     *                       maquina e um que nao abre a tela (Internet Explorer em servidor antigo)
     *                       e o analista vai colar o endereco no Edge ou no Chrome
     */
    public static int executar(Path pastaInstalacao, PrintStream saida, boolean abrirNavegador) {
        try {
            return executarInterno(pastaInstalacao, saida, abrirNavegador);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OK;
        } catch (IOException | RuntimeException e) {
            saida.println("ERRO inesperado: " + e);
            return ERRO_INESPERADO;
        }
    }

    private static int executarInterno(Path pastaInstalacao, PrintStream saida, boolean abrirNavegador)
            throws IOException, InterruptedException {
        saida.println("=======================================================================");
        saida.println(" MAPEADOR DE PASTAS - Robo Tareffa");
        saida.println("=======================================================================");

        Path arquivoParametros = pastaInstalacao.resolve("parametros.txt");
        Optional<Parametros> parametrosOpt = ParametrosLoader.carregar(arquivoParametros);
        if (parametrosOpt.isEmpty()) {
            saida.println("CONFIGURACAO INVALIDA em " + arquivoParametros);
            saida.println("  " + TestarPastas.explicarParametrosInvalidos(arquivoParametros));
            saida.println();
            saida.println("O mapeador precisa da PASTA_INICIAL para mostrar as pastas do cliente.");
            saida.println("Preencha o parametros.txt (Passo 2 do GUIA_INSTALACAO.md) e rode o mapear.bat de novo.");
            return CONFIGURACAO_INVALIDA;
        }
        Parametros parametros = parametrosOpt.get();

        String pastaInicial = parametros.getPastaInicial();
        if (!DiagnosticoPastaInicial.existe(pastaInicial)) {
            saida.println("PASTA_INICIAL NAO ENCONTRADA: " + pastaInicial);
            if (DiagnosticoPastaInicial.ehLetraDeUnidade(pastaInicial)) {
                saida.println("  " + DiagnosticoPastaInicial.dicaDeUnidadeMapeada(pastaInicial));
            } else {
                saida.println("  Confira no parametros.txt se o caminho esta certo e se este usuario tem acesso.");
            }
            return CONFIGURACAO_INVALIDA;
        }

        MapeadorServidor servidor = MapeadorServidor.iniciar(new MapeadorApi(pastaInstalacao, parametros));

        saida.println("Pasta inicial : " + pastaInicial);
        saida.println("Usuario       : " + System.getProperty("user.name", "?"));
        saida.println();
        saida.println(abrirNavegador
                ? "A tela do mapeador vai abrir no navegador. Se nao abrir, copie este endereco:"
                : "Abra este endereco no Edge ou no Chrome:");
        saida.println();
        saida.println("  " + servidor.endereco());
        saida.println();
        saida.println("MANTENHA ESTA JANELA ABERTA enquanto usa o mapeador.");
        saida.println("Para encerrar: clique em \"Encerrar\" na tela, ou feche esta janela.");
        saida.flush();
        if (abrirNavegador) {
            abrirNavegador(servidor.endereco(), saida);
        }

        String motivo = servidor.aguardarEncerramento();
        saida.println();
        saida.println("Mapeador encerrado: " + motivo);
        return OK;
    }

    private static void abrirNavegador(String endereco, PrintStream saida) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(endereco));
                return;
            }
        } catch (IOException | RuntimeException e) {
            // Segue para o caminho do proprio Windows abaixo.
        }
        try {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", endereco).start();
        } catch (IOException e) {
            saida.println("(Nao foi possivel abrir o navegador sozinho - copie o endereco acima.)");
        }
    }
}
