package dev.omatheusmesmo.qlawkus.secrets;

import java.util.Set;

/**
 * The curated list of configuration properties that hold a secret value, used by the config editor to
 * decide which fields route to the encrypted keystore ({@link KeystoreSecretWriter}) instead of a
 * plain runtime-toggle or build-time override. Config metadata has no {@code isSecret} flag on a
 * {@code @ConfigMapping} accessor, so this list is maintained by hand.
 *
 * <p><strong>Source of truth in prose:</strong> the "What to store" table in
 * {@code site/content/secrets.adoc}. Keep the two in sync - this class exists only because the editor
 * needs a machine-readable form of that same table, not to replace it.
 */
public final class SecretPropertyCatalog {

    private static final Set<String> SECRET_PROPERTIES = Set.of(
            "quarkus.langchain4j.openai.\"primary\".api-key",
            "qlawkus.embedding.api-key",
            "qlawkus.admin.password-hash",
            "qlawkus.google.auth.client-secret",
            "qlawkus.google.vault.encryption-passphrase",
            "qlawkus.messaging.discord.bot-token",
            "qlawkus.messaging.telegram.bot-token",
            "qlawkus.messaging.transcription.api-key",
            "quarkus.datasource.password",
            "quarkus.datasource.\"google-auth\".password",
            "quarkus.datasource.\"brag\".password"
    );

    private SecretPropertyCatalog() {
    }

    /**
     * Whether {@code property} is a known secret property. TTS provider keys
     * ({@code qlawkus.messaging.tts.providers.<id>.api-key}) are matched by suffix since {@code <id>}
     * is dynamic.
     */
    public static boolean isSecret(String property) {
        if (property == null) {
            return false;
        }
        return SECRET_PROPERTIES.contains(property)
                || (property.startsWith("qlawkus.messaging.tts.providers.") && property.endsWith(".api-key"));
    }
}
