package security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Senhas novas usam PBKDF2 com salt; aceita SHA-256 antigo apenas para migração. */
public final class Senhas {

    private static final int ITERACOES = 310_000;
    private static final int TAMANHO_CHAVE = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Senhas() { }

    public static String criar(String senha) {
        byte[] salt = new byte[16];
        ALEATORIO.nextBytes(salt);
        byte[] hash = derivar(senha, salt, ITERACOES);
        return "pbkdf2$" + ITERACOES + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verificar(String senha, String armazenada) {
        if (senha == null || armazenada == null) {
            return false;
        }
        if (ehLegada(armazenada)) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(senha.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) {
                    hex.append(String.format("%02x", b));
                }
                return MessageDigest.isEqual(hex.toString().getBytes(StandardCharsets.US_ASCII),
                        armazenada.toLowerCase().getBytes(StandardCharsets.US_ASCII));
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("SHA-256 indisponível.", e);
            }
        }
        try {
            String[] partes = armazenada.split("\\$", -1);
            if (partes.length != 4 || !"pbkdf2".equals(partes[0])) {
                return false;
            }
            int iteracoes = Integer.parseInt(partes[1]);
            if (iteracoes < 1 || iteracoes > 1_000_000) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            return MessageDigest.isEqual(esperado, derivar(senha, salt, iteracoes));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean ehLegada(String armazenada) {
        return armazenada != null && armazenada.matches("(?i)[0-9a-f]{64}");
    }

    private static byte[] derivar(String senha, byte[] salt, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), salt, iteracoes, TAMANHO_CHAVE);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 indisponível.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
