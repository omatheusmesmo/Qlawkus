package dev.omatheusmesmo.qlawkus.tools.google.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesEncryptorTest {

    private static final String TEST_PASSPHRASE = "my-secure-vault-passphrase";

    private AesEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new AesEncryptor(TEST_PASSPHRASE);
    }

    @Test
    void encryptDecrypt_roundtrip() {
        String plaintext = "my-secret-refresh-token";
        String encrypted = encryptor.encrypt(plaintext);
        String decrypted = encryptor.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String plaintext = "same-input";
        String first = encryptor.encrypt(plaintext);
        String second = encryptor.encrypt(plaintext);

        assertNotEquals(first, second);
    }

    @Test
    void encrypt_producesBase64() {
        String encrypted = encryptor.encrypt("token");
        assertTrue(encrypted.matches("^[A-Za-z0-9+/]+=*$"));
    }

    @Test
    void decrypt_failsOnGarbageInput() {
        assertThrows(RuntimeException.class, () -> encryptor.decrypt("not-valid-base64!!!"));
    }

    @Test
    void decrypt_failsWithWrongPassphrase() {
        AesEncryptor encryptorA = new AesEncryptor("passphrase-A");
        AesEncryptor encryptorB = new AesEncryptor("passphrase-B");

        String encrypted = encryptorA.encrypt("secret-data");

        assertThrows(RuntimeException.class, () -> encryptorB.decrypt(encrypted));
    }

    @Test
    void encryptDecrypt_emptyString() {
        String encrypted = encryptor.encrypt("");
        assertEquals("", encryptor.decrypt(encrypted));
    }

    @Test
    void encryptDecrypt_unicodeContent() {
        String plaintext = "töken-ção-日本語";
        String encrypted = encryptor.encrypt(plaintext);
        assertEquals(plaintext, encryptor.decrypt(encrypted));
    }

    @Test
    void samePassphraseDifferentEncryptors_decryptWorks() {
        AesEncryptor enc1 = new AesEncryptor("same-pass");
        AesEncryptor enc2 = new AesEncryptor("same-pass");

        String encrypted = enc1.encrypt("shared-secret");
        assertEquals("shared-secret", enc2.decrypt(encrypted));
    }
}
