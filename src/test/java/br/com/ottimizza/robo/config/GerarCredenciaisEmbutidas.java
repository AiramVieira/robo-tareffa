package br.com.ottimizza.robo.config;

import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

/**
 * Gera o bloco de constantes de {@code CredenciaisEmbutidas.java} a partir dos valores reais.
 *
 * <p>Vive em {@code src/test/java} de proposito: fontes de teste nao entram no {@code robo.jar},
 * entao o pacote entregue ao cliente leva o codigo que <i>decifra</i> e nunca a ferramenta que
 * <i>cifra</i>. Estar no mesmo pacote de {@link SegredoEmbutido} e o que da acesso ao
 * {@code cifrar} package-private, sem precisar torna-lo publico.
 *
 * <p>Uso (Windows):
 * <pre>
 *   mvnw.cmd -o test-compile
 *   java -cp "target/classes;target/test-classes" br.com.ottimizza.robo.config.GerarCredenciaisEmbutidas
 * </pre>
 *
 * <p>Le do console (sem eco, quando o terminal permite) e imprime o bloco pronto para colar.
 * Nao grava nada em disco: um arquivo intermediario com as credenciais em texto puro seria
 * exatamente o que esta mudanca veio eliminar.
 */
public final class GerarCredenciaisEmbutidas {

    /**
     * Um unico leitor para as quatro perguntas. Um {@code BufferedReader} por pergunta parece
     * inocente e nao e: o primeiro puxa TODA a entrada disponivel para o buffer dele e devolve so
     * a primeira linha, entao os leitores seguintes acham o stream vazio e retornam null. Com
     * entrada redirecionada (que e como se testa isto), o resultado sao tres credenciais vazias
     * cifradas sem nenhum erro - o pior modo de falha possivel para esta ferramenta.
     */
    private static BufferedReader leitor;

    private static boolean avisoJaImpresso;

    public static void main(String[] args) throws IOException {
        System.out.println("Gerador de credenciais embutidas do robo.");
        System.out.println("Informe os quatro valores REAIS. Nada e gravado em disco.");
        System.out.println();

        String authServerUrl = exigirPreenchido("AUTH_SERVER_URL",
                ler("AUTH_SERVER_URL  (ex.: https://auth.exemplo.com.br)"));
        String clientId = exigirPreenchido("CLIENT_ID", ler("CLIENT_ID"));
        String clientSecret = exigirPreenchido("CLIENT_SECRET", ler("CLIENT_SECRET"));
        String senhaIntegracao = exigirPreenchido("SENHA_INTEGRACAO", ler("SENHA_INTEGRACAO"));

        System.out.println();
        System.out.println("Cole o bloco abaixo em CredenciaisEmbutidas.java, substituindo as quatro linhas:");
        System.out.println();
        imprimirConstante("AUTH_SERVER_URL", authServerUrl);
        imprimirConstante("CLIENT_ID", clientId);
        imprimirConstante("CLIENT_SECRET", clientSecret);
        imprimirConstante("SENHA_INTEGRACAO", senhaIntegracao);
        System.out.println();
        System.out.println("Depois: mvnw.cmd -o clean package  (o pacote sai em target/dist/)");
    }

    /** Cifrar um valor vazio produz um pacote que so falha la na maquina do cliente. */
    private static String exigirPreenchido(String nome, String valor) {
        if (valor == null || valor.isBlank()) {
            System.err.println();
            System.err.println("ERRO: " + nome + " veio vazio. Nada foi gerado.");
            System.exit(1);
        }
        return valor;
    }

    private static void imprimirConstante(String nome, String valor) {
        System.out.println("    static final String " + nome + " = \"" + SegredoEmbutido.cifrar(valor) + "\";");
    }

    /**
     * Sem eco quando ha console de verdade. Rodando pela IDE ou com a entrada redirecionada,
     * {@link System#console()} e nulo e nao ha o que fazer alem de ler a linha normalmente - por
     * isso o aviso, para ninguem digitar uma senha achando que ela nao esta aparecendo.
     */
    private static String ler(String rotulo) throws IOException {
        Console console = System.console();
        if (console != null) {
            return new String(console.readPassword("%s: ", rotulo)).trim();
        }
        if (!avisoJaImpresso) {
            System.out.println("(AVISO: sem console interativo - o que voce digitar VAI aparecer na tela)");
            avisoJaImpresso = true;
        }
        System.out.print(rotulo + ": ");
        System.out.flush();
        if (leitor == null) {
            leitor = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }
        String linha = leitor.readLine();
        return linha == null ? "" : linha.trim();
    }

    private GerarCredenciaisEmbutidas() {
    }
}
