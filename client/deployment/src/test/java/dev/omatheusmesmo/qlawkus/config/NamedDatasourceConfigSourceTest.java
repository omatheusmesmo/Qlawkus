package dev.omatheusmesmo.qlawkus.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretConfigSourceFactory;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Locks how a tool's own database is switched on at boot. The published image carries the Brag and
 * Google extensions but no URL for their databases, so the datasource, Hibernate and Flyway of each
 * stay at Quarkus' inactive default until the operator supplies a URL, and only then are they turned on.
 * Without a URL all three are switched off explicitly.
 */
class NamedDatasourceConfigSourceTest {

    private static final String ENV = "quarkus.datasource.brag.jdbc.url";
    private static final String PASSWORD = "fixture-store-password";

    @TempDir
    Path directory;

    private final ConfigSourceFactory factory =
            context -> NamedDatasourceConfigSource.sources(context, "brag");

    @Test
    void withoutAUrlInProdTheWholeDatabaseStaysOff() {
        SmallRyeConfig config = config("prod", Map.of());

        assertEquals("false", config.getConfigValue("quarkus.datasource.\"brag\".active").getValue());
        assertEquals("false", config.getConfigValue("quarkus.hibernate-orm.\"brag\".active").getValue());
        assertEquals("false", config.getConfigValue("quarkus.flyway.\"brag\".active").getValue());
        assertNull(config.getConfigValue("quarkus.datasource.\"brag\".jdbc.url").getValue());
    }

    @Test
    void outsideProdDevServicesMayProvideTheDatabaseSoNothingIsSwitchedOff() {
        SmallRyeConfig config = config("test", Map.of());

        assertNull(config.getConfigValue("quarkus.datasource.\"brag\".active").getValue());
        assertNull(config.getConfigValue("quarkus.hibernate-orm.\"brag\".active").getValue());
    }

    @Test
    void outsideProdWithDevServicesDisabledTheWholeDatabaseStaysOff() {
        SmallRyeConfig config = config("test", Map.of("quarkus.datasource.\"brag\".devservices.enabled", "false"));

        assertEquals("false", config.getConfigValue("quarkus.hibernate-orm.\"brag\".active").getValue());
    }

    @Test
    void aBlankUrlCountsAsNone() {
        SmallRyeConfig config = config("prod", Map.of(ENV, "  "));

        assertEquals("false", config.getConfigValue("quarkus.hibernate-orm.\"brag\".active").getValue());
    }

    @Test
    void aUrlPassesThroughAndSwitchesTheDatabaseOn() {
        SmallRyeConfig config = config("prod", Map.of(ENV, "jdbc:postgresql://db:5432/qlawkus_brag"));

        assertEquals("jdbc:postgresql://db:5432/qlawkus_brag",
                config.getConfigValue("quarkus.datasource.\"brag\".jdbc.url").getValue());
        assertEquals("true", config.getConfigValue("quarkus.datasource.\"brag\".active").getValue());
        assertEquals("true", config.getConfigValue("quarkus.hibernate-orm.\"brag\".active").getValue());
        assertEquals("true", config.getConfigValue("quarkus.flyway.\"brag\".active").getValue());
    }

    @Test
    void aUrlStoredInTheKeystoreCountsAsConfigured() {
        Path keystore = directory.resolve("secrets.p12");
        new KeystoreSecretWriter(secretsConfig(keystore)).setSecret(ENV, "jdbc:postgresql://db:5432/qlawkus_brag");

        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withProfile("prod")
                .withSources(new KeystoreSecretConfigSourceFactory())
                .withSources(factory)
                .withSources(new PropertiesConfigSource(Map.of(
                        "qlawkus.secrets.keystore-path", keystore.toString(),
                        "qlawkus.secrets.keystore-password", PASSWORD), "application", 250))
                .build();

        assertEquals("true", config.getConfigValue("quarkus.datasource.\"brag\".active").getValue());
    }

    private SmallRyeConfig config(String profile, Map<String, String> application) {
        return new SmallRyeConfigBuilder()
                .withProfile(profile)
                .withSources(factory)
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
