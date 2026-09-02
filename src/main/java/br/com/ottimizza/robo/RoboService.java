package br.com.ottimizza.robo;

import br.com.ottimizza.robo.auth.OttimizzaAuthClient;
import br.com.ottimizza.robo.config.CredenciaisAuth;
import br.com.ottimizza.robo.config.Parametros;
import br.com.ottimizza.robo.config.ParametrosLoader;
import br.com.ottimizza.robo.discovery.PastaAlvoResolver;
import br.com.ottimizza.robo.logging.ErroLogger;
import br.com.ottimizza.robo.processamento.CicloProcessamento;
import br.com.ottimizza.robo.upload.TareffaStorageClient;

import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Ponto de entrada do servico: entra em um ciclo continuo (secao 2/5 do readme), recarregando
 * {@code parametros.txt} a cada volta.
 */
public final class RoboService {

    public static void main(String[] args) throws URISyntaxException {
        Path pastaInstalacao = descobrirPastaInstalacao();
        Path arquivoParametros = pastaInstalacao.resolve("parametros.txt");

        ErroLogger erroLogger = new ErroLogger(pastaInstalacao);
        System.out.println("Robo iniciado. Pasta de instalacao: " + pastaInstalacao);

        CredenciaisAuth credenciais;
        try {
            credenciais = CredenciaisAuth.carregar(pastaInstalacao);
        } catch (IllegalStateException e) {
            erroLogger.registrar("Nao foi possivel iniciar: " + e.getMessage(), null);
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        HttpClient httpClient = HttpClient.newHttpClient();
        OttimizzaAuthClient authClient = new OttimizzaAuthClient(credenciais, httpClient);
        TareffaStorageClient storageClient = new TareffaStorageClient(httpClient);
        CicloProcessamento ciclo = new CicloProcessamento(authClient, storageClient, erroLogger);
        PastaAlvoResolver resolver = new PastaAlvoResolver(erroLogger);

        while (true) {
            int intervaloSegundos = executarCiclo(arquivoParametros, resolver, ciclo, erroLogger);
            try {
                TimeUnit.SECONDS.sleep(Math.max(1, intervaloSegundos));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static int executarCiclo(Path arquivoParametros, PastaAlvoResolver resolver,
                                      CicloProcessamento ciclo, ErroLogger erroLogger) {
        Optional<Parametros> parametrosOpt = ParametrosLoader.carregar(arquivoParametros);
        if (parametrosOpt.isEmpty()) {
            erroLogger.registrar("Configuracao invalida em parametros.txt - ciclo ignorado", null);
            return ParametrosLoader.INTERVALO_VARREDURA_PADRAO_SEGUNDOS;
        }
        Parametros parametros = parametrosOpt.get();
        System.out.println("Ciclo: contabilidade=" + parametros.getContabilidade()
                + " customizacao=" + parametros.getCustomizacao());

        PastaAlvoResolver.Resultado resultado = resolver.resolver(parametros);
        if (resultado instanceof PastaAlvoResolver.Resultado.ConfiguracaoInvalida invalida) {
            erroLogger.registrar("Configuracao de descoberta de pastas invalida ("
                    + invalida.motivo() + ") - ciclo ignorado", null);
            return parametros.getIntervaloVarreduraSegundos();
        }

        List<Path> pastasAlvo = ((PastaAlvoResolver.Resultado.Sucesso) resultado).pastasAlvo();
        for (Path pastaAlvo : pastasAlvo) {
            ciclo.processarPastaAlvo(pastaAlvo, parametros);
        }

        return parametros.getIntervaloVarreduraSegundos();
    }

    private static Path descobrirPastaInstalacao() throws URISyntaxException {
        Path localCodigo = Path.of(RoboService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return Files.isRegularFile(localCodigo) ? localCodigo.getParent() : localCodigo;
    }

    private RoboService() {
    }
}
