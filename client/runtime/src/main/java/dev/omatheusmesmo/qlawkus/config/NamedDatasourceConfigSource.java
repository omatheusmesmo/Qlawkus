package dev.omatheusmesmo.qlawkus.config;

import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretConfigSourceFactory;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Switches a tool's own named database on once the operator gives it a URL, so an extension that owns a
 * datasource can ride in the published image without a database to migrate.
 *
 * <p>The published image is built with no URL for these datasources, which leaves them, their Hibernate ORM
 * persistence unit and their Flyway at Quarkus' inactive default. A URL that arrives at boot (the
 * {@code QUARKUS_DATASOURCE_<NAME>_JDBC_URL} variable or the console keystore) is passed on as the datasource URL and turns the three on explicitly.
 * Without one all three are switched off explicitly, because the persistence unit would otherwise fail the
 * boot looking for its datasource, except outside {@code prod} while dev services may still provide the database. The keystore is not visible to a plain factory, see
 * {@link KeystoreSecretConfigSourceFactory#withStoredSecrets}.
 */
public final class NamedDatasourceConfigSource {

    private static final int ORDINAL = 90;

    private NamedDatasourceConfigSource() {
    }

    /**
     * The sources for one named datasource.
     *
     * @param plainContext the context the factory received
     * @param datasource the datasource and persistence unit name, such as {@code brag}
     */
    public static Iterable<ConfigSource> sources(ConfigSourceContext plainContext, String datasource) {
        ConfigSourceContext context = KeystoreSecretConfigSourceFactory.withStoredSecrets(plainContext);
        ConfigValue url = context.getValue("quarkus.datasource." + datasource + ".jdbc.url");
        boolean configured = url != null && url.getValue() != null && !url.getValue().isBlank();
        if (!configured && devServicesMayProvide(context, datasource)) {
            return List.of();
        }
        String active = String.valueOf(configured);
        Map<String, String> values = new HashMap<>();
        if (configured) {
            values.put("quarkus.datasource.\"" + datasource + "\".jdbc.url", url.getValue().trim());
        }
        values.put("quarkus.datasource.\"" + datasource + "\".active", active);
        values.put("quarkus.hibernate-orm.\"" + datasource + "\".active", active);
        values.put("quarkus.flyway.\"" + datasource + "\".active", active);
        return List.of(new PropertiesConfigSource(values, "qlawkus-datasource-" + datasource, ORDINAL));
    }

    /**
     * Dev services start a database and hand its URL over only after this factory has run, so outside
     * {@code prod} the missing URL is not final unless the operator turned them off for this datasource.
     */
    private static boolean devServicesMayProvide(ConfigSourceContext context, String datasource) {
        if (context.getProfiles().contains("prod")) {
            return false;
        }
        for (String key : List.of("quarkus.datasource.\"" + datasource + "\".devservices.enabled",
                "quarkus.datasource." + datasource + ".devservices.enabled")) {
            ConfigValue enabled = context.getValue(key);
            if (enabled != null && "false".equalsIgnoreCase(enabled.getValue())) {
                return false;
            }
        }
        return true;
    }
}
