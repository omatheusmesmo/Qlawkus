package dev.omatheusmesmo.qlawkus.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattens the free-form, dot-namespaced {@code runtime:} toggle tree into plain
 * {@code property -> value} pairs, the shape a MicroProfile {@code ConfigSource} publishes. Keys are
 * used <em>verbatim</em> as Quarkus configuration property names (e.g.
 * {@code qlawkus.skill-hub.approval-mode}), with no prefix translation, so the same key works across
 * the inconsistent existing roots ({@code qlawkus.skill-hub.*} vs {@code qlawkus.agent.*}). The
 * manifest may write them flat-dotted or nested; both flatten to the same property name.
 *
 * <p>Quarkus-free, so the model stays shared between the pom generator and the running app.
 */
public final class RuntimeToggles {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private RuntimeToggles() {
    }

    /**
     * Flattens a toggle tree to dotted property names. Nested maps extend the key with a dot, lists
     * become indexed keys ({@code key[0]}, {@code key[1]}, ...), and scalars are stringified. Null
     * values and a null tree yield no entries. Iteration order is preserved.
     */
    public static Map<String, String> flatten(Map<String, Object> toggles) {
        Map<String, String> flat = new LinkedHashMap<>();
        if (toggles != null) {
            flattenInto("", toggles, flat);
        }
        return flat;
    }

    /**
     * Parses a standalone runtime-override document (a YAML map of toggles, not a full manifest) and
     * flattens it. Blank or empty input yields an empty map, so an absent override is a no-op.
     *
     * @throws InvalidManifestException if the YAML is malformed
     */
    public static Map<String, String> parseOverride(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tree = YAML.readValue(yaml, Map.class);
            return flatten(tree);
        } catch (IOException e) {
            throw new InvalidManifestException("Invalid runtime override: " + e.getMessage(), e);
        }
    }

    /**
     * Serializes a flat toggle map back to a standalone runtime-override YAML document, the write-side
     * counterpart of {@link #parseOverride}. Keys are written as literal (possibly dotted) top-level
     * scalar keys, never unflattened into nested structure - {@link #parseOverride} reads either shape
     * identically, and flat is simpler for a writer to patch a single key in without disturbing the
     * rest. An empty map renders to an empty document.
     */
    public static String renderOverride(Map<String, String> toggles) {
        try {
            return YAML.writeValueAsString(toggles == null ? Map.of() : toggles);
        } catch (IOException e) {
            throw new InvalidManifestException("Failed to render runtime override: " + e.getMessage(), e);
        }
    }

    private static void flattenInto(String prefix, Map<String, Object> node, Map<String, String> out) {
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            put(key, entry.getValue(), out);
        }
    }

    private static void put(String key, Object value, Map<String, String> out) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> child = (Map<String, Object>) map;
            flattenInto(key, child, out);
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                put(key + "[" + i + "]", list.get(i), out);
            }
        } else {
            out.put(key, String.valueOf(value));
        }
    }
}
