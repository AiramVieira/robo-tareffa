package br.com.ottimizza.robo.config;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cifra/decifra os segredos que viajam embutidos no {@code robo.jar} (AES-256-GCM).
 *
 * <h2>O que isto e, e o que nao e</h2>
 * <p>Isto e <b>ofuscacao, nao sigilo</b>. A chave viaja no mesmo jar que o texto cifrado - quem
 * tiver o jar e um descompilador recupera os valores em minutos. Nao existe forma de embutir um
 * segredo num binario entregue ao cliente e mante-lo secreto <i>daquele</i> cliente: o programa
 * precisa decifrar para usar, entao tudo que ele precisa para decifrar esta ali.
 *
 * <p>O que isto de fato entrega, e que era o pedido: a credencial deixa de ficar <b>legivel em
 * texto puro num arquivo ao lado do robo</b>. Antes, qualquer pessoa que abrisse a pasta de
 * instalacao - ou um backup dela, ou o compartilhamento de rede por onde o pacote e distribuido -
 * lia a senha de integracao no bloco de notas. Isso cobre o risco real e comum (exposicao casual,
 * copia acidental, print de tela em chamado de suporte) e nao cobre um cliente decidido a extrair
 * a credencial.
 *
 * <p>Se um dia for preciso sigilo real contra o proprio cliente, o caminho nao e criptografia
 * melhor aqui: e a credencial nunca chegar na maquina dele - um token por instalacao, emitido pelo
 * servidor e revogavel individualmente.
 *
 * <h2>Formato</h2>
 * <p>Um blob e {@code base64(iv || ciphertext_com_tag)}, com IV de 12 bytes sorteado a cada
 * cifragem. A chave sai de PBKDF2-HMAC-SHA256 sobre uma passphrase montada em pedacos, o
 * suficiente para que um {@code strings robo.jar} nao entregue nada pronto.
 *
 * <p>Sem dependencia nova e sem modulo jlink novo: AES/GCM e PBKDF2 vem do provider {@code SunJCE},
 * dentro de {@code java.base}.
 */
final class SegredoEmbutido {

    private static final int TAMANHO_IV_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final int TAMANHO_CHAVE_BITS = 256;
    private static final int ITERACOES_PBKDF2 = 120_000;

    /**
     * Montada em pedacos de proposito: concatenada em tempo de execucao, ela nao aparece como uma
     * unica string legivel no pool de constantes do .class.
     */
    private static final String[] PARTES_PASSPHRASE = {
            "rb-tf@", "2026::", "ottmzz", "-integ", "racao#", "v2"
    };

    private static final byte[] SAL = {
            (byte) 0x8f, (byte) 0x27, (byte) 0xd1, (byte) 0x4a,
            (byte) 0x63, (byte) 0xbe, (byte) 0x05, (byte) 0x9c,
            (byte) 0x31, (byte) 0xa7, (byte) 0xe8, (byte) 0x52,
            (byte) 0x7f, (byte) 0x10, (byte) 0xc4, (byte) 0x6d
    };

    /** PBKDF2 com 120 mil iteracoes custa ~100 ms; derivar uma vez so mantem o startup barato. */
    private static volatile SecretKey chaveMemoizada;

    private SegredoEmbutido() {
    }

    /** Usado pelo gerador (fonte de teste) para produzir as constantes de {@link CredenciaisEmbutidas}. */
    static String cifrar(String textoClaro) {
        try {
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave(), new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoClaro.getBytes(StandardCharsets.UTF_8));

            byte[] blob = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(cifrado, 0, blob, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(blob);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar segredo embutido", e);
        }
    }

    /**
     * @return o texto claro, ou {@code ""} se o blob estiver vazio - o placeholder de uma
     *         constante ainda nao preenchida. A validacao de "faltando" e de {@link CredenciaisAuth},
     *         que sabe dizer qual credencial e; aqui nao ha contexto para uma mensagem util.
     */
    static String decifrar(String blobBase64) {
        if (blobBase64 == null || blobBase64.isBlank()) {
            return "";
        }
        try {
            byte[] blob = Base64.getDecoder().decode(blobBase64.trim());
            if (blob.length <= TAMANHO_IV_BYTES) {
                throw new IllegalStateException("Blob cifrado menor que o proprio IV");
            }
            byte[] iv = Arrays.copyOfRange(blob, 0, TAMANHO_IV_BYTES);
            byte[] cifrado = Arrays.copyOfRange(blob, TAMANHO_IV_BYTES, blob.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave(), new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Acontece quando o blob foi gerado por outra versao da passphrase/do sal, ou foi
            // truncado na hora de colar. Falhar aqui e o certo: seguir com lixo produziria um 401
            // no primeiro upload, longe da causa.
            throw new IllegalStateException(
                    "Nao foi possivel decifrar um segredo embutido no robo.jar - o pacote foi montado"
                            + " com credenciais geradas por outra versao do gerador. Regere as constantes"
                            + " de CredenciaisEmbutidas e reconstrua o pacote.", e);
        }
    }

    private static SecretKey chave() throws GeneralSecurityException {
        SecretKey local = chaveMemoizada;
        if (local != null) {
            return local;
        }
        synchronized (SegredoEmbutido.class) {
            if (chaveMemoizada == null) {
                PBEKeySpec spec = new PBEKeySpec(
                        String.join("", PARTES_PASSPHRASE).toCharArray(), SAL,
                        ITERACOES_PBKDF2, TAMANHO_CHAVE_BITS);
                byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
                chaveMemoizada = new SecretKeySpec(bytes, "AES");
            }
            return chaveMemoizada;
        }
    }
}
