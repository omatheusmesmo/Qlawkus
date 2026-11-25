package dev.omatheusmesmo.qlawkus.secrets;

import dev.omatheusmesmo.qlawkus.config.SecretsConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Writes secrets into the {@code qlawkus.secrets} PKCS12 keystore using the JDK {@link java.security.KeyStore}
 * API, so a secret can be onboarded without {@code keytool}. An entry written here is stored exactly
 * the way {@code keytool -importpass} stores it (see {@link KeystoreSecrets}), so the read side picks
 * it up unchanged on the next boot.
 *
 * <p>The store password is {@code qlawkus.secrets.keystore-password} when set. Otherwise the first write
 * generates one into an owner-only file beside the keystore ({@link KeystorePasswordFile}) and later reads
 * and writes reuse it, so a deployment with one volume needs no environment file. It is both the store
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
        Path file = keystorePath();
        KeystoreSecrets.put(file, writePassword(file), alias, value);
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
     * Resolves an alias from the current keystore, including writes made after boot. This is an
     * internal extension API, never a tool or an HTTP response: callers must keep the value out of
     * their previews, results and logs. Ordinary configuration remains a snapshot until restart.
     */
    public Optional<String> readSecret(String alias) {
        requireAlias(alias);
        Path file = keystorePath();
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.ofNullable(KeystoreSecrets.read(file, password(), alias));
    }

    /**
     * Removes the entry stored under {@code alias}. Returns {@code true} if an entry was removed,
     * {@code false} if the alias (or the keystore) was absent.
     */
    public boolean deleteSecret(String alias) {
        requireAlias(alias);
        Path file = keystorePath();
        return Files.isRegularFile(file) && KeystoreSecrets.delete(file, password(), alias);
    }

    private char[] password() {
        return configuredPassword()
                .or(() -> KeystorePasswordFile.read(keystorePath()))
                .orElseThrow(() -> new IllegalStateException(
                        "Set qlawkus.secrets.keystore-password (for example via "
                                + "QLAWKUS_SECRETS_KEYSTORE_PASSWORD) to unlock the existing keystore"));
    }

    private char[] writePassword(Path file) {
        return configuredPassword().orElseGet(() -> KeystorePasswordFile.readOrCreate(file));
    }

    private Optional<char[]> configuredPassword() {
        return config.keystorePassword()
                .filter(p -> !p.isBlank())
                .map(String::toCharArray);
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
