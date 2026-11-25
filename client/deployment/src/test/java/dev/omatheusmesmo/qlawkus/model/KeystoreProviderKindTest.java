package dev.omatheusmesmo.qlawkus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.omatheusmesmo.qlawkus.config.SecretsConfig;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretConfigSourceFactory;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Locks that a provider chosen and keyed only through the keystore (what the console wizard writes)
 * reaches every model derived from it. The regression: the keystore factory ran after the kind factory,
 * which therefore never saw the stored key or kind, so the embedding model was built with the
 * {@code no-key} placeholder against the OpenAI endpoint and every agent turn failed on retrieval even
 * though the chat model had its key.
 */
class KeystoreProviderKindTest {

    private static final String PRIMARY_API_KEY = "quarkus.langchain4j.openai.\"primary\".api-key";
    private static final String PRIMARY_BASE_URL = "quarkus.langchain4j.openai.\"primary\".base-url";
    private static final String EMBEDDING_API_KEY = "qlawkus.embedding.api-key";
    private static final String EMBEDDING_BASE_URL = "qlawkus.embedding.base-url";
    private static final String NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1";
    private static final String PASSWORD = "fixture-store-password";

    @TempDir
    Path directory;

    @Test
    void keyAndKindStoredInTheKeystoreRouteChatAndEmbeddingToTheProvider() {
        Path keystore = directory.resolve("secrets.p12");
        KeystoreSecretWriter writer = new KeystoreSecretWriter(secretsConfig(keystore));
        writer.setSecret("qlawkus.openai.kind", "nvidia");
        writer.setSecret(PRIMARY_API_KEY, "stored-provider-key");

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new KeystoreSecretConfigSourceFactory())
                .withSources(new LlmKindConfigSourceFactory())
                .withSources(new PropertiesConfigSource(Map.of(
                        "qlawkus.secrets.keystore-path", keystore.toString(),
                        "qlawkus.secrets.keystore-password", PASSWORD), "application", 250))
                .build();

        assertEquals("stored-provider-key", config.getConfigValue(PRIMARY_API_KEY).getValue());
        assertEquals("stored-provider-key", config.getConfigValue(EMBEDDING_API_KEY).getValue());
        assertEquals(NVIDIA_BASE_URL, config.getConfigValue(PRIMARY_BASE_URL).getValue());
        assertEquals(NVIDIA_BASE_URL, config.getConfigValue(EMBEDDING_BASE_URL).getValue());
    }

    private SecretsConfig secretsConfig(Path keystore) {
        return new SecretsConfig() {
            @Override
            public String keystorePath() {
                return keystore.toString();
            }

            @Override
            public Optional<String> keystorePassword() {
                return Optional.of(PASSWORD);
            }

            @Override
            public int keystoreOrdinal() {
                return 350;
            }
        };
    }
}
