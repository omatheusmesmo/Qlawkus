package dev.omatheusmesmo.qlawkus.store.pg;

import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretConfigSourceFactory;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Chooses the cognition store at boot, so one image serves both the database-free quickstart and a
 * Postgres deployment. An explicit {@code qlawkus.cognition.backend} wins; otherwise a configured
 * {@code quarkus.datasource.jdbc.url} means {@code pgvector} and its absence means {@code markdown}.
 *
 * <p>Choosing {@code markdown} also switches the default datasource, Hibernate ORM and Flyway off
 * ({@code quarkus.*.active=false}), because the Postgres extension is on the classpath either way and
 * would otherwise try to migrate a database that is not there. Choosing Postgres switches them on
 * explicitly: Quarkus defaults them to inactive when no URL was known at build time, which is the case
 * for the published image, whose URL arrives at boot. The URL and the choice are read through
 * the keystore, so what the console wizard stores applies on the next restart; the keystore is not
 * visible to a plain factory (see {@link KeystoreSecretConfigSourceFactory#withStoredSecrets}).
 *
 * <p>A value that is not a backend is not coerced to {@code markdown}: it is passed through untouched and
 * the datasource stays off, so the configuration error from {@code CognitionConfig} is the failure the
 * operator sees at boot rather than a database error.
 */
public class StoreBackendConfigSourceFactory implements ConfigSourceFactory {

    static final String BACKEND = "qlawkus.cognition.backend";
    static final String DATASOURCE_URL = "quarkus.datasource.jdbc.url";
    private static final int ORDINAL = 90;

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext plainContext) {
        ConfigSourceContext context = KeystoreSecretConfigSourceFactory.withStoredSecrets(plainContext);
        String chosen = present(context.getValue(BACKEND));
        String backend = chosen != null ? chosen.toLowerCase() : present(context.getValue(DATASOURCE_URL)) != null ? "pgvector" : "markdown";

        Map<String, String> values = new HashMap<>();
        if (chosen == null) {
            values.put(BACKEND, backend);
        }
        String active = String.valueOf("pgvector".equals(backend) || "hybrid".equals(backend));
        values.put("quarkus.datasource.active", active);
        values.put("quarkus.hibernate-orm.active", active);
        values.put("quarkus.flyway.active", active);
        return List.of(new PropertiesConfigSource(values, "qlawkus-store-backend", ORDINAL));
    }

    private static String present(ConfigValue value) {
        return value == null || value.getValue() == null || value.getValue().isBlank() ? null : value.getValue().trim();
    }
}
