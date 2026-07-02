package dev.omatheusmesmo.qlawkus.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;

import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import dev.omatheusmesmo.qlawkus.composition.RuntimeToggles;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;

/**
 * Publishes the runtime-toggle tier as two config sources: the baked-in {@code runtime:} defaults
 * from the classpath manifest (low ordinal) and the external override file (higher, but below env).
 * The mirror of {@code KeystoreSecretConfigSourceFactory}: same ordinal discipline, opposite intent -
 * secrets outrank env so a stored secret wins, toggles yield to env so a deploy-time {@code -D} or
 * environment variable always wins over a persisted toggle.
 *
 * <p>Lenient by design: a missing or malformed manifest, or a missing override file, contributes
 * nothing and never stops boot. The toggle keys are used verbatim as configuration property names
 * (see {@link RuntimeToggles}).
 */
public final class RuntimeToggleConfigSourceFactory implements ConfigSourceFactory {

    private static final String ENABLED = "qlawkus.runtime.enabled";
    private static final String MANIFEST = "qlawkus.runtime.manifest";
    private static final String OVERRIDE_PATH = "qlawkus.runtime.override-path";
    private static final String BAKED_ORDINAL = "qlawkus.runtime.baked-ordinal";
    private static final String OVERRIDE_ORDINAL = "qlawkus.runtime.override-ordinal";

    private static final int DEFAULT_BAKED_ORDINAL = 250;
    private static final int DEFAULT_OVERRIDE_ORDINAL = 290;
    private static final String DEFAULT_OVERRIDE_PATH = "~/" + CompositionPaths.DEFAULT_RUNTIME_OVERRIDE;

    private static final String BAKED_NAME = "QlawkusRuntimeTogglesBaked";
    private static final String OVERRIDE_NAME = "QlawkusRuntimeTogglesOverride";

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        if ("false".equalsIgnoreCase(value(context, ENABLED))) {
            return List.of();
        }
        List<ConfigSource> sources = new ArrayList<>();
        Map<String, String> baked = bakedDefaults(context);
        if (!baked.isEmpty()) {
            sources.add(new PropertiesConfigSource(baked, BAKED_NAME,
                    ordinal(context, BAKED_ORDINAL, DEFAULT_BAKED_ORDINAL)));
        }
        Map<String, String> override = externalOverride(context);
        if (!override.isEmpty()) {
            sources.add(new PropertiesConfigSource(override, OVERRIDE_NAME,
                    ordinal(context, OVERRIDE_ORDINAL, DEFAULT_OVERRIDE_ORDINAL)));
        }
        return sources;
    }

    private static Map<String, String> bakedDefaults(ConfigSourceContext context) {
        String location = orDefault(value(context, MANIFEST), CompositionPaths.DEFAULT_MANIFEST);
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(location);
        if (in == null) {
            return Map.of();
        }
        try (in) {
            CompositionManifest manifest = CompositionManifestParser.parse(in);
            return RuntimeToggles.flatten(manifest.runtime());
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Map<String, String> externalOverride(ConfigSourceContext context) {
        String path = orDefault(value(context, OVERRIDE_PATH), DEFAULT_OVERRIDE_PATH);
        Path file = Path.of(expandHome(path));
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            return RuntimeToggles.parseOverride(Files.readString(file));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static int ordinal(ConfigSourceContext context, String name, int fallback) {
        String configured = value(context, name);
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String expandHome(String path) {
        return path.startsWith("~") ? System.getProperty("user.home") + path.substring(1) : path;
    }

    private static String value(ConfigSourceContext context, String name) {
        ConfigValue value = context.getValue(name);
        return value == null ? null : value.getValue();
    }
}
