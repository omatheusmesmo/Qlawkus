package dev.omatheusmesmo.qlawkus.secrets;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;

/**
 * Publishes the secrets in the {@code qlawkus.secrets} PKCS12 keystore as a config source ranked
 * above environment variables, so a stored secret is authoritative. The built-in SmallRye keystore
 * config source loads at a fixed ordinal of 100 (below {@code application.properties}, {@code .env}
 * and env vars) with no knob to raise it, which would let an env var shadow a stored secret. This
 * factory reads the keystore named by the {@code qlawkus.secrets.*} convention and republishes every
 * entry at {@code qlawkus.secrets.keystore-ordinal} (default 350, below only system properties).
 *
 * <p>Each keystore alias is the configuration property it supplies; the secret value is the entry's
 * encoded bytes read as UTF-8 (how {@code keytool -importpass} stores it). The keystore is resolved
 * on the filesystem first, then the classpath. Resolution is lenient: an absent keystore contributes
 * nothing, so a fresh install still boots on its {@code .env}.
 */
public final class KeystoreSecretConfigSourceFactory implements ConfigSourceFactory {

    private static final String PATH = "qlawkus.secrets.keystore-path";
    private static final String PASSWORD = "qlawkus.secrets.keystore-password";
    private static final String ORDINAL = "qlawkus.secrets.keystore-ordinal";
    private static final int DEFAULT_ORDINAL = 350;
    private static final String NAME = "QlawkusKeystoreSecrets";

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        String path = value(context, PATH);
        if (path == null || path.isBlank()) {
            path = defaultKeystorePath();
        }
        URL location = resolve(path);
        if (location == null) {
            return List.of();
        }
        String password = value(context, PASSWORD);
        char[] secret = password == null ? new char[0] : password.toCharArray();
        Map<String, String> secrets = KeystoreSecrets.readAll(location, secret);
        if (secrets.isEmpty()) {
            return List.of();
        }
        return List.of(new PropertiesConfigSource(secrets, NAME, ordinal(context)));
    }

    private static URL resolve(String path) {
        try {
            Path file = KeystoreSecrets.resolve(path);
            if (Files.isRegularFile(file)) {
                return file.toUri().toURL();
            }
        } catch (Exception ignored) {
        }
        return Thread.currentThread().getContextClassLoader().getResource(path);
    }

    private static int ordinal(ConfigSourceContext context) {
        String configured = value(context, ORDINAL);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_ORDINAL;
        }
        return Integer.parseInt(configured.trim());
    }

    private static String value(ConfigSourceContext context, String name) {
        ConfigValue value = context.getValue(name);
        return value == null ? null : value.getValue();
    }

    private static String defaultKeystorePath() {
        return System.getProperty("user.home") + "/.qlawkus/secrets.p12";
    }
}
