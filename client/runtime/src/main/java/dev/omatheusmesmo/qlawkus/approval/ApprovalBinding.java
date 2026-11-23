package dev.omatheusmesmo.qlawkus.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical form of a tool call's arguments and the hash that binds an approval to them.
 *
 * <p>Canonical means object keys sorted at every depth, with no insignificant whitespace, so two
 * spellings of the same arguments produce one hash. The hash covers the tool name as well, so an
 * approval for one tool can never be replayed against another that happens to take the same keys.
 */
public final class ApprovalBinding {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HexFormat HEX = HexFormat.of();

    private ApprovalBinding() {
    }

    /**
     * Canonical JSON for a tool call's arguments. A blank value is a call with no arguments.
     *
     * @throws IllegalArgumentException when the arguments are not a JSON object
     */
    public static String canonicalArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.isBlank()) {
            return "{}";
        }
        JsonNode tree;
        try {
            tree = JSON.readTree(rawArguments);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Tool arguments are not valid JSON", e);
        }
        if (tree == null || !tree.isObject()) {
            throw new IllegalArgumentException("Tool arguments must be a JSON object");
        }
        try {
            return JSON.writeValueAsString(sorted(tree));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Tool arguments cannot be serialised", e);
        }
    }

    /** Lower-case hex SHA-256 over the tool name, a newline, and the canonical arguments. */
    public static String hash(String toolName, String canonicalArguments) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((toolName + "\n" + canonicalArguments).getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM specification", e);
        }
    }

    private static JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                fields.put(field.getKey(), sorted(field.getValue()));
            }
            ObjectNode ordered = JSON.createObjectNode();
            fields.forEach(ordered::set);
            return ordered;
        }
        if (node.isArray()) {
            ArrayNode ordered = JSON.createArrayNode();
            node.forEach(item -> ordered.add(sorted(item)));
            return ordered;
        }
        return node;
    }
}
