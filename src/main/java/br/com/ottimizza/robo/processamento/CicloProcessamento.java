package br.com.ottimizza.robo.processamento;

import br.com.ottimizza.robo.auth.AutenticadorToken;
import br.com.ottimizza.robo.config.Parametros;
import br.com.ottimizza.robo.logging.ErroLogger;
import br.com.ottimizza.robo.upload.EnviadorArquivo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Orquestra o processamento de uma pasta-alvo (secao 7 do readme): normaliza extensoes,
 * localiza arquivos aceitos e, para cada um, reserva + autentica + envia, um de cada vez.
 */
public final class CicloProcessamento {

    private static final Set<String> EXTENSOES_ACEITAS = Set.of(".pdf", ".txt", ".csv", ".rar", ".zip");

    private final ExtensaoNormalizador normalizador = new ExtensaoNormalizador();
    private final ArquivoReservador reservador = new ArquivoReservador();
    private final AutenticadorToken autenticador;
    private final EnviadorArquivo enviador;
    private final ErroLogger erroLogger;

    public CicloProcessamento(AutenticadorToken autenticador, EnviadorArquivo enviador, ErroLogger erroLogger) {
        this.autenticador = autenticador;
        this.enviador = enviador;
        this.erroLogger = erroLogger;
    }

    public void processarPastaAlvo(Path pastaAlvo, Parametros parametros) {
        try {
            Path pastaLeitura = CaminhosPasta.pastaLeitura(pastaAlvo, parametros.getPastaEnviar());
            Files.createDirectories(pastaLeitura);

            Path pastaEnviados = CaminhosPasta.pastaEnviados(pastaAlvo, parametros.isEnviadoDatado(), LocalDate.now());

            normalizador.normalizar(pastaLeitura);

            for (Path arquivo : listarArquivosAceitos(pastaLeitura)) {
                processarArquivo(arquivo, pastaEnviados, pastaAlvo, parametros);
            }
        } catch (Exception e) {
            erroLogger.registrar("Falha ao processar pasta-alvo " + pastaAlvo, e);
        }
    }

    private List<Path> listarArquivosAceitos(Path pastaLeitura) throws IOException {
        try (Stream<Path> stream = Files.list(pastaLeitura)) {
            return stream.filter(Files::isRegularFile)
                    .filter(arquivo -> EXTENSOES_ACEITAS.contains(extensao(arquivo)))
                    .collect(Collectors.toList());
        }
    }

    private static String extensao(Path arquivo) {
        String nome = arquivo.getFileName().toString();
        int posicaoPonto = nome.lastIndexOf('.');
        return posicaoPonto < 0 ? "" : nome.substring(posicaoPonto).toLowerCase(Locale.ROOT);
    }

    private void processarArquivo(Path arquivo, Path pastaEnviados, Path pastaAlvo, Parametros parametros) {
        Path arquivoReservado;
        try {
            arquivoReservado = reservador.reservar(arquivo, pastaEnviados);
            System.out.println("Arquivo reservado: " + arquivoReservado);
        } catch (Exception e) {
            colocarEmQuarentena(arquivo, pastaAlvo, e);
            return;
        }

        boolean sucesso = enviar(arquivoReservado, parametros);

        try {
            reservador.finalizar(arquivoReservado);
        } catch (Exception e) {
            erroLogger.registrar("Falha ao remover marcador do arquivo " + arquivoReservado, e);
        }

        if (!sucesso) {
            erroLogger.registrar("Falha no envio do arquivo " + arquivoReservado, null);
        }
    }

    private boolean enviar(Path arquivoReservado, Parametros parametros) {
        try {
            if (!Files.exists(arquivoReservado)) {
                return false;
            }
            byte[] conteudo = Files.readAllBytes(arquivoReservado);
            String nomeArquivo = arquivoReservado.getFileName().toString();
            String contabilidade = parametros.getContabilidade();

            String token = autenticador.obterTokenAtual(contabilidade);
            int status = enviador.enviar(contabilidade, nomeArquivo, conteudo, token);

            if (status == 401) {
                String novoToken = autenticador.reautenticar(contabilidade);
                status = enviador.enviar(contabilidade, nomeArquivo, conteudo, novoToken);
            }

            return status >= 200 && status < 300;
        } catch (Exception e) {
            erroLogger.registrar("Excecao durante envio de " + arquivoReservado, e);
            return false;
        }
    }

    private void colocarEmQuarentena(Path arquivo, Path pastaAlvo, Exception causa) {
        erroLogger.registrar("Falha ao ler/preparar arquivo " + arquivo, causa);
        try {
            Path pastaErros = CaminhosPasta.pastaErros(pastaAlvo);
            Files.createDirectories(pastaErros);
            Path destino = pastaErros.resolve(arquivo.getFileName());
            Files.move(arquivo, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e2) {
            erroLogger.registrar("Falha ao mover arquivo com erro para ERROS: " + arquivo, e2);
        }
    }
}
