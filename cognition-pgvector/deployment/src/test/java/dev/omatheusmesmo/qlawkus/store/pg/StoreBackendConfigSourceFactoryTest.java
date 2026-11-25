package dev.omatheusmesmo.qlawkus.store.pg;

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
 * Locks how the one published image picks its store at boot: an explicit {@code qlawkus.cognition.backend}
 * wins, otherwise a configured datasource URL means Postgres and its absence means markdown, and the
 * markdown choice switches the datasource, Hibernate and Flyway off so nothing tries to reach a database.
 */
class StoreBackendConfigSourceFactoryTest {

    private static final String BACKEND = "qlawkus.cognition.backend";
    private static final String URL = "quarkus.datasource.jdbc.url";
    private static final String PASSWORD = "fixture-store-password";

    @TempDir
    Path directory;

    @Test
    void noUrlAndNoChoiceRunsOnMarkdownWithTheDatabaseSwitchedOff() {
        SmallRyeConfig config = config(Map.of());

        assertEquals("markdown", config.getConfigValue(BACKEND).getValue());
        assertSwitchedOff(config);
    }

    @Test
    void aConfiguredUrlMeansPostgresAndSwitchesTheDatabaseOn() {
        SmallRyeConfig config = config(Map.of(URL, "jdbc:postgresql://db:5432/qlawkus"));

        assertEquals("pgvector", config.getConfigValue(BACKEND).getValue());
        assertSwitchedOn(config);
    }

    @Test
    void anExplicitChoiceWinsOverTheUrl() {
        SmallRyeConfig config = config(Map.of(BACKEND, "markdown", URL, "jdbc:postgresql://db:5432/qlawkus"));

        assertEquals("markdown", config.getConfigValue(BACKEND).getValue());
        assertSwitchedOff(config);
    }

    @Test
    void hybridSwitchesTheDatabaseOn() {
        SmallRyeConfig config = config(Map.of(BACKEND, "hybrid"));

        assertEquals("hybrid", config.getConfigValue(BACKEND).getValue());
        assertSwitchedOn(config);
    }

    @Test
    void anUnknownChoiceLeavesTheDatabaseOffSoTheConfigurationErrorComesFirst() {
        SmallRyeConfig config = config(Map.of(BACKEND, "mongo", URL, "jdbc:postgresql://db:5432/qlawkus"));

        assertSwitchedOff(config);
    }

    @Test
    void aUrlStoredInTheKeystoreCountsAsConfigured() {
        Path keystore = directory.resolve("secrets.p12");
        KeystoreSecretWriter writer = new KeystoreSecretWriter(secretsConfig(keystore));
        writer.setSecret(URL, "jdbc:postgresql://db:5432/qlawkus");

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new KeystoreSecretConfigSourceFactory())
                .withSources(new StoreBackendConfigSourceFactory())
                .withSources(new PropertiesConfigSource(Map.of(
                        "qlawkus.secrets.keystore-path", keystore.toString(),
                        "qlawkus.secrets.keystore-password", PASSWORD), "application", 250))
                .build();

        assertEquals("pgvector", config.getConfigValue(BACKEND).getValue());
        assertSwitchedOn(config);
    }

    /**
   * Quarkus defaults these to inactive when no URL was known at build time, which is the case in the
   * published image, so choosing Postgres has to turn them on explicitly.
   */
  private void assertSwitchedOn(SmallRyeConfig config) {
    assertEquals("true", config.getConfigValue("quarkus.datasource.active").getValue());
    assertEquals("true", config.getConfigValue("quarkus.hibernate-orm.active").getValue());
    assertEquals("true", config.getConfigValue("quarkus.flyway.active").getValue());
  }

  private void assertSwitchedOff(SmallRyeConfig config) {
        assertEquals("false", config.getConfigValue("quarkus.datasource.active").getValue());
        assertEquals("false", config.getConfigValue("quarkus.hibernate-orm.active").getValue());
        assertEquals("false", config.getConfigValue("quarkus.flyway.active").getValue());
    }

    private SmallRyeConfig config(Map<String, String> application) {
        return new SmallRyeConfigBuilder()
                .withSources(new StoreBackendConfigSourceFactory())
                .withSources(new PropertiesConfigSource(application, "application", 250))
                .build();
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
