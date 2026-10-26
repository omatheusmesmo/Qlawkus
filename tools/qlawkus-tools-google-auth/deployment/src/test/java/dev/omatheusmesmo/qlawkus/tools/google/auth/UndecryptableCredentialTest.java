package dev.omatheusmesmo.qlawkus.tools.google.auth;

import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(VaultPassphraseMismatchProfile.class)
class UndecryptableCredentialTest {

    @Inject
    CredentialVaultService vault;

    @Inject
    @QlawTool
    GoogleAuthTool authTool;

    @BeforeEach
    @Transactional
    void seedCredentialEncryptedUnderAnotherPassphrase() {
        GoogleCredential.deleteAll();
        AesEncryptor previous = new AesEncryptor("passphrase-a");

        GoogleCredential credential = new GoogleCredential();
        credential.provider = "google";
        credential.encryptedRefreshToken = previous.encrypt("refresh-token-value");
        credential.encryptedAccessToken = previous.encrypt("access-token-value");
        credential.tokenType = "Bearer";
        credential.persist();
    }

    @Test
    void startupWithUndecryptableCredentialDoesNotThrow() {
        assertDoesNotThrow(() -> vault.onStartup(new StartupEvent()));
    }

    @Test
    void undecryptableCredentialIsReportedAsNotAuthorized() {
        vault.onStartup(new StartupEvent());

        assertNull(vault.getAccessToken());
        assertNull(vault.getDecryptedRefreshToken());
        assertTrue(authTool.checkGoogleAuthorization().contains("not_authorized"));
    }

    @Test
    void renewalDoesNotThrowForUndecryptableCredential() {
        assertDoesNotThrow(() -> {
            vault.renewAccessToken();
            vault.renewAccessToken();
        });
    }

    @Test
    void undecryptableCredentialIsKeptForALaterCorrectPassphrase() {
        vault.onStartup(new StartupEvent());

        assertNotNull(GoogleCredential.findByProvider("google"));
    }
}
