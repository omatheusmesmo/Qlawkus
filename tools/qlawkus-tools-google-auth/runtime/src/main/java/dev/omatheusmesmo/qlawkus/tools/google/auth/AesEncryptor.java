package dev.omatheusmesmo.qlawkus.tools.google.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.quarkus.logging.Log;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public class AesEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int ARGON2_SALT_LENGTH = 16;
    private static final int ARGON2_ITERATIONS = 3;
    private static final int ARGON2_MEMORY_KB = 65536;
    private static final int ARGON2_PARALLELISM = 4;

    private final char[] passphrase;

    public AesEncryptor(String passphrase) {
        this.passphrase = passphrase.toCharArray();
    }

    public String encrypt(String plaintext) {
        try {
            byte[] salt = new byte[ARGON2_SALT_LENGTH];
            new SecureRandom().nextBytes(salt);

            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKeySpec keySpec = deriveKey(salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[salt.length + iv.length + ciphertext.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(iv, 0, combined, salt.length, iv.length);
            System.arraycopy(ciphertext, 0, combined, salt.length + iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            Log.errorf(e, "Encryption failed");
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);

            byte[] salt = new byte[ARGON2_SALT_LENGTH];
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - ARGON2_SALT_LENGTH - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, salt, 0, salt.length);
            System.arraycopy(combined, ARGON2_SALT_LENGTH, iv, 0, iv.length);
            System.arraycopy(combined, ARGON2_SALT_LENGTH + GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            SecretKeySpec keySpec = deriveKey(salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.errorf(e, "Decryption failed");
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private SecretKeySpec deriveKey(byte[] salt) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withIterations(ARGON2_ITERATIONS)
                .withMemoryAsKB(ARGON2_MEMORY_KB)
                .withParallelism(ARGON2_PARALLELISM)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] key = new byte[KEY_LENGTH_BYTES];
        generator.generateBytes(passphrase, key);

        return new SecretKeySpec(key, "AES");
    }
}
