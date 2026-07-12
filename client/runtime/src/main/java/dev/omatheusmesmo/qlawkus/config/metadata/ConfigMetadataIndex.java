package dev.omatheusmesmo.qlawkus.config.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.secrets.SecretPropertyCatalog;
import io.quarkus.runtime.annotations.ConfigPhase;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the {@code quarkus-config-doc} metadata bundled in every extension's runtime jar
 * ({@code META-INF/quarkus-config-doc/quarkus-config-model.json} +
 * {@code quarkus-config-javadoc.json}), the same source the {@code quarkus-config-doc-maven-plugin}
 * reads at doc-generation time - so the config editor never re-derives property metadata by
 * reflection or duplicates a hand-maintained list. Built once at construction by aggregating every
 * matching resource visible on the context classloader (one contribution per jar, exactly like
 * {@code META-INF/services} discovery), so it automatically reflects whichever optional extensions are
 * actually on the classpath - no central catalog to keep in sync.
 *
 * <p>Scope is {@code qlawkus.*} in full, plus {@code quarkus.*} properties named in the curated
 * allowlist resource ({@link #ALLOWLIST_LOCATION}) - the framework's own config surface is mostly
 * internal tuning the agent operator does not touch day to day. Properties listed in
 * {@link SecretPropertyCatalog} are excluded regardless of scope: a secret is never a candidate for
 * plain display or a plain override, so it never reaches a consumer of this index in the first place.
 */
@ApplicationScoped
public class ConfigMetadataIndex {

    private static final String MODEL_LOCATION = "META-INF/quarkus-config-doc/quarkus-config-model.json";
    private static final String JAVADOC_LOCATION = "META-INF/quarkus-config-doc/quarkus-config-javadoc.json";
    private static final String ALLOWLIST_LOCATION = "qlawkus/config-editor-quarkus-allowlist.txt";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<ConfigRootMetadata> roots;
    private final Map<String, ConfigPropertyMetadata> byProperty;

    public ConfigMetadataIndex() {
        this(Thread.currentThread().getContextClassLoader());
    }

    ConfigMetadataIndex(ClassLoader loader) {
        Map<String, String> javadoc = loadJavadoc(loader);
        Set<String> quarkusAllowlist = loadAllowlist(loader);
        List<ConfigRootMetadata> loadedRoots = new ArrayList<>();
        for (JsonNode modelDoc : loadJsonDocs(loader, MODEL_LOCATION)) {
            for (JsonNode root : modelDoc.path("configRoots")) {
                ConfigRootMetadata parsed = parseRoot(root, javadoc, quarkusAllowlist);
                if (!parsed.properties().isEmpty()) {
                    loadedRoots.add(parsed);
                }
            }
        }
        this.roots = List.copyOf(loadedRoots);
        Map<String, ConfigPropertyMetadata> index = new LinkedHashMap<>();
        for (ConfigRootMetadata root : roots) {
            for (ConfigPropertyMetadata item : root.properties()) {
                index.put(item.property(), item);
            }
        }
        this.byProperty = Map.copyOf(index);
    }

    /** All in-scope config roots, one per {@code @ConfigMapping} prefix, in discovery order. */
    public List<ConfigRootMetadata> roots() {
        return roots;
    }

    /** Metadata for a single property, empty when it is undocumented or out of scope. */
    public Optional<ConfigPropertyMetadata> find(String property) {
        return Optional.ofNullable(byProperty.get(property));
    }

    private static ConfigRootMetadata parseRoot(JsonNode root, Map<String, String> javadoc, Set<String> quarkusAllowlist) {
        String prefix = root.path("prefix").asText("");
        String extensionName = root.path("extension").path("name").asText(null);
        List<ConfigPropertyMetadata> properties = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            if (!"io.quarkus.annotation.processor.documentation.config.model.ConfigProperty"
                    .equals(item.path("@class").asText())) {
                continue;
            }
            String property = item.path("path").path("property").asText(null);
            if (property == null || !inScope(property, quarkusAllowlist) || SecretPropertyCatalog.isSecret(property)) {
                continue;
            }
            properties.add(parseProperty(item, property, prefix, javadoc));
        }
        return new ConfigRootMetadata(prefix, extensionName, List.copyOf(properties));
    }

    private static boolean inScope(String property, Set<String> quarkusAllowlist) {
        if (property.startsWith("qlawkus.")) {
            return true;
        }
        return property.startsWith("quarkus.") && quarkusAllowlist.contains(property);
    }

    private static ConfigPropertyMetadata parseProperty(JsonNode item, String property, String prefix,
                                                          Map<String, String> javadoc) {
        String env = item.path("path").path("environmentVariable").asText(null);
        ConfigPhase phase = parsePhase(item.path("phase").asText(null));
        String type = item.path("type").asText(null);
        String typeDescription = item.path("typeDescription").asText(null);
        String defaultValue = item.hasNonNull("defaultValue") ? item.get("defaultValue").asText() : null;
        boolean optional = item.path("optional").asBoolean(false);
        List<String> allowedValues = new ArrayList<>();
        JsonNode values = item.path("enumAcceptedValues").path("values");
        values.fieldNames().forEachRemaining(allowedValues::add);
        String sourceType = item.path("sourceType").asText(null);
        String sourceElementName = item.path("sourceElementName").asText(null);
        String description = sourceType != null && sourceElementName != null
                ? javadoc.get(sourceType + "." + sourceElementName)
                : null;
        return new ConfigPropertyMetadata(property, prefix, env, phase, type, typeDescription,
                defaultValue, optional, List.copyOf(allowedValues), description);
    }

    private static ConfigPhase parsePhase(String value) {
        if (value == null) {
            return ConfigPhase.RUN_TIME;
        }
        try {
            return ConfigPhase.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ConfigPhase.RUN_TIME;
        }
    }

    private static Map<String, String> loadJavadoc(ClassLoader loader) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (JsonNode doc : loadJsonDocs(loader, JAVADOC_LOCATION)) {
            JsonNode elements = doc.path("elements");
            elements.fields().forEachRemaining(entry ->
                    merged.put(entry.getKey(), entry.getValue().path("description").asText(null)));
        }
        return merged;
    }

    private static Set<String> loadAllowlist(ClassLoader loader) {
        Set<String> allowlist = new LinkedHashSet<>();
        try (InputStream in = loader.getResourceAsStream(ALLOWLIST_LOCATION)) {
            if (in == null) {
                return allowlist;
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    allowlist.add(trimmed);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + ALLOWLIST_LOCATION, e);
        }
        return allowlist;
    }

    private static List<JsonNode> loadJsonDocs(ClassLoader loader, String location) {
        List<JsonNode> docs = new ArrayList<>();
        try {
            Enumeration<URL> resources = loader.getResources(location);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream in = url.openStream()) {
                    docs.add(JSON.readTree(in));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + location + " from the classpath", e);
        }
        return docs;
    }
}
