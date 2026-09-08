package br.com.ottimizza.robo.discovery;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Cache em disco (secao 6.2 do readme) da lista de pastas-alvo ja mapeadas, guardado na
 * propria raiz configurada ({@code PASTA_INICIAL}), para nao precisar refazer a varredura
 * completa a cada ciclo de varredura de arquivos.
 */
public final class PastaAlvoCache {

    private static final String NOME_ARQUIVO = ".robo_pastas_alvo.cache.json";

    private final Path arquivoCache;

    /**
     * Tolerante a campo desconhecido para que um jar mais antigo, reinstalado na maquina do
     * cliente, ainda consiga ler um cache gravado por uma versao mais nova.
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public PastaAlvoCache(Path pastaInicial) {
        this.arquivoCache = pastaInicial.resolve(NOME_ARQUIVO);
    }

    public Optional<CacheData> ler() {
        if (!Files.isRegularFile(arquivoCache)) {
            return Optional.empty();
        }
        try {
            CacheData dados = objectMapper.readValue(arquivoCache.toFile(), CacheData.class);
            return Optional.ofNullable(dados);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void salvar(List<String> caminhos, Instant geradoEm, String configFingerprint) throws IOException {
        CacheData dados = new CacheData();
        dados.pastas = caminhos;
        dados.geradoEm = geradoEm.toString();
        dados.configFingerprint = configFingerprint;
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(arquivoCache.toFile(), dados);
    }

    public boolean expirado(CacheData dados, int intervaloRemapeamentoHoras) {
        Instant geradoEm;
        try {
            geradoEm = Instant.parse(dados.geradoEm);
        } catch (Exception e) {
            return true;
        }
        return Duration.between(geradoEm, Instant.now()).toHours() >= intervaloRemapeamentoHoras;
    }

    /**
     * Se o cache foi gerado pela mesma configuracao de descoberta que esta em vigor agora.
     *
     * <p>Um cache gravado por uma versao anterior nao tem o campo, e nesse caso o resultado e
     * {@code false} de proposito: um cliente que atualizou o jar e ja tinha editado as regras
     * precisa remapear, senao continuaria servindo a lista antiga indefinidamente. O custo e um
     * remapeamento extra no primeiro ciclo apos a atualizacao.
     */
    public boolean fingerprintCoincide(CacheData dados, String fingerprintAtual) {
        return dados.configFingerprint != null
                && !dados.configFingerprint.isBlank()
                && dados.configFingerprint.equals(fingerprintAtual);
    }

    public static final class CacheData {
        public List<String> pastas;
        public String geradoEm;
        public String configFingerprint;
    }
}
