package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class GoogleCredentialTest {

    @BeforeEach
    @Transactional
    void cleanup() {
        GoogleCredential.deleteAll();
    }

    @Test
    @Transactional
    void findByProvider_returnsPersistedCredential() {
        GoogleCredential credential = new GoogleCredential();
        credential.provider = "google";
        credential.encryptedRefreshToken = "encrypted-value";
        credential.persist();

        GoogleCredential found = GoogleCredential.findByProvider("google");

        assertNotNull(found);
        assertEquals("encrypted-value", found.encryptedRefreshToken);
    }

    @Test
    @Transactional
    void findByProvider_returnsNullWhenNotFound() {
        assertNull(GoogleCredential.findByProvider("nonexistent"));
    }

    @Test
    @Transactional
    void timestamps_setOnPersist() {
        GoogleCredential credential = new GoogleCredential();
        credential.provider = "google-timestamps";
        credential.encryptedRefreshToken = "tok";
        credential.persist();

        assertNotNull(credential.createdAt);
        assertNotNull(credential.updatedAt);
    }
}
