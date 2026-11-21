package dev.omatheusmesmo.qlawkus.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.config.SecretsConfig;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeystoreSecretLiveReadTest {

    @TempDir
    Path directory;

    @Test
    void readerObservesWritesOverwriteAndDeletionWithoutRestart() {
        SecretsConfig config = config(Optional.of("fixture-store-password"));
        KeystoreSecretWriter writer = new KeystoreSecretWriter(config);
        KeystoreSecretWriter reader = new KeystoreSecretWriter(config);
        String alias = "qlawkus.mcp.servers.fixture.headers.authorization";

        assertTrue(reader.readSecret(alias).isEmpty());
        writer.setSecret(alias, "Bearer first-fixture-token");
        assertEquals(Optional.of("Bearer first-fixture-token"), reader.readSecret(alias));
        writer.setSecret(alias, "Bearer second-fixture-token");
        assertEquals(Optional.of("Bearer second-fixture-token"), reader.readSecret(alias));
        assertTrue(writer.deleteSecret(alias));
        assertTrue(reader.readSecret(alias).isEmpty());
        assertFalse(writer.deleteSecret(alias));
    }

    @Test
    void missingStoreOrAliasHasNoValue() {
        KeystoreSecretWriter noPassword = new KeystoreSecretWriter(config(Optional.empty()));
        assertTrue(noPassword.readSecret("missing.alias").isEmpty());
        assertFalse(noPassword.deleteSecret("missing.alias"), "an absent vault has nothing to delete and needs no password");
        KeystoreSecretWriter writer = new KeystoreSecretWriter(config(Optional.of("fixture-store-password")));
        writer.setSecret("present.alias", "fixture-value");
        assertTrue(writer.readSecret("missing.alias").isEmpty());
    }

    @Test
    void existingStoreRequiresTheCorrectPassword() {
        new KeystoreSecretWriter(config(Optional.of("fixture-store-password")))
                .setSecret("fixture.alias", "fixture-value");
        KeystoreSecretWriter noPassword = new KeystoreSecretWriter(config(Optional.empty()));
        assertThrows(IllegalStateException.class, () -> noPassword.readSecret("fixture.alias"));
        KeystoreSecretWriter wrongPassword = new KeystoreSecretWriter(config(Optional.of("wrong-fixture-password")));
        assertThrows(IllegalStateException.class, () -> wrongPassword.readSecret("fixture.alias"));
    }

    @Test
    void blankAliasesAreRejected() {
        KeystoreSecretWriter writer = new KeystoreSecretWriter(config(Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> writer.readSecret(" "));
        assertThrows(IllegalArgumentException.class, () -> writer.readSecret(null));
    }

    @Test
    void mcpCredentialValuesAreSecretButRegistryReferencesAreMetadata() {
        assertTrue(SecretPropertyCatalog.isSecret("qlawkus.mcp.servers.docs.headers.authorization"));
        assertTrue(SecretPropertyCatalog.isSecret("qlawkus.mcp.servers.local.environment.API_TOKEN"));
        assertFalse(SecretPropertyCatalog.isSecret("qlawkus.mcp.servers.docs.header-secret-refs.Authorization"));
        assertFalse(SecretPropertyCatalog.isSecret("qlawkus.mcp.servers.local.environment-secret-refs.API_TOKEN"));
    }

    private SecretsConfig config(Optional<String> password) {
        return new SecretsConfig() {
            @Override
            public String keystorePath() {
                return directory.resolve("live-read.p12").toString();
            }

            @Override
            public Optional<String> keystorePassword() {
                return password;
            }

            @Override
            public int keystoreOrdinal() {
                return 350;
            }
        };
    }
}
