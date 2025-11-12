
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class AESGCMUtil {
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int TAG_LENGTH_BIT = 128;

    private static final SecureRandom secureRandom = new SecureRandom();

    private static SecretKey deriveKey(char[] password, byte[] salt, int iterations, int keyLen) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLen);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static String encrypt(String plainText, String password) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        SecretKey key = deriveKey(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

        byte[] plaintextBytes = plainText.getBytes(StandardCharsets.UTF_8);
        byte[] cipherText = cipher.doFinal(plaintextBytes);

        byte[] out = new byte[salt.length + iv.length + cipherText.length];
        System.arraycopy(salt, 0, out, 0, salt.length);
        System.arraycopy(iv, 0, out, salt.length, iv.length);
        System.arraycopy(cipherText, 0, out, salt.length + iv.length, cipherText.length);

        return Base64.getEncoder().encodeToString(out);
    }

    public static String decrypt(String base64Input, String password) throws Exception {
        byte[] all = Base64.getDecoder().decode(base64Input);

        if (all.length < SALT_LENGTH + IV_LENGTH + 1) {
            throw new IllegalArgumentException("Invalid input format");
        }

        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[IV_LENGTH];
        byte[] cipherText = new byte[all.length - SALT_LENGTH - IV_LENGTH];

        System.arraycopy(all, 0, salt, 0, SALT_LENGTH);
        System.arraycopy(all, SALT_LENGTH, iv, 0, IV_LENGTH);
        System.arraycopy(all, SALT_LENGTH + IV_LENGTH, cipherText, 0, cipherText.length);

        SecretKey key = deriveKey(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        byte[] plain = cipher.doFinal(cipherText);
        return new String(plain, StandardCharsets.UTF_8);
    }
}
