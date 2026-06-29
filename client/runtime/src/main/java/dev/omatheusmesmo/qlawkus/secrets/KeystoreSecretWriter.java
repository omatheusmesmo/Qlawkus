package dev.omatheusmesmo.qlawkus.secrets;

import dev.omatheusmesmo.qlawkus.config.SecretsConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes secrets into the {@code qlawkus.secrets} PKCS12 keystore using the JDK {@link java.security.KeyStore}
 * API, so a secret can be onboarded without {@code keytool}. An entry written here is stored exactly
 * the way {@code keytool -importpass} stores it (see {@link KeystoreSecrets}), so the read side picks
 * it up unchanged on the next boot.
 *
 * <p>Writing requires {@code qlawkus.secrets.keystore-password} to be set: it is both the store
 * password and the per-entry protection password, matching how the read side unlocks entries. The
 * keystore file is created on first write if absent. The secret value is never logged.
 */
@ApplicationScoped
public class KeystoreSecretWriter {

    private final SecretsConfig config;

    @Inject
    public KeystoreSecretWriter(SecretsConfig config) {
        this.config = config;
    }

    /**
     * Stores {@code value} under {@code alias} (the config property the secret supplies), creating the
     * keystore if it does not exist yet. Overwrites an existing entry with the same alias.
     */
    public void setSecret(String alias, String value) {
        requireAlias(alias);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Secret value must not be empty");
        }
        KeystoreSecrets.put(keystorePath(), password(), alias, value);
    }

    /**
     * Lists the aliases currently stored, never their values. Returns an empty list when the keystore
     * does not exist yet.
     */
    public List<String> aliases() {
        Path file = keystorePath();
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        return KeystoreSecrets.aliases(file, password());
    }

    /**
     * Removes the entry stored under {@code alias}. Returns {@code true} if an entry was removed,
     * {@code false} if the alias (or the keystore) was absent.
     */
    public boolean deleteSecret(String alias) {
        requireAlias(alias);
        return KeystoreSecrets.delete(keystorePath(), password(), alias);
    }

    private char[] password() {
        return config.keystorePassword()
                .filter(p -> !p.isBlank())
                .map(String::toCharArray)
                .orElseThrow(() -> new IllegalStateException(
                        "Set qlawkus.secrets.keystore-password (for example via "
                                + "QLAWKUS_SECRETS_KEYSTORE_PASSWORD) before writing secrets"));
    }

    private Path keystorePath() {
        return KeystoreSecrets.resolve(config.keystorePath());
    }

    private static void requireAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("Secret alias must not be blank");
        }
    }
}
