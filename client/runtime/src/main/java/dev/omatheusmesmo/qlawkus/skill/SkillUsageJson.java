package dev.omatheusmesmo.qlawkus.skill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads and writes the usage sidecar through Jackson's tree model, field by field, so no type is
 * found by reflection: a native image needs no registration for it, and a type added to
 * {@link SkillUsage} fails the round-trip test instead of failing silently on a running instance.
 * The layout is the one the reflective mapper wrote, so existing files keep reading.
 */
final class SkillUsageJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private SkillUsageJson() {
  }

  static Map<String, SkillUsage> read(String json) throws JsonProcessingException {
    Map<String, SkillUsage> usage = new LinkedHashMap<>();
    JsonNode root = MAPPER.readTree(json);
    if (root == null || !root.isObject()) {
      return usage;
    }
    root.properties().forEach(entry -> usage.put(entry.getKey(), usage(entry.getValue())));
    return usage;
  }

  static String write(Map<String, SkillUsage> usage) throws JsonProcessingException {
    ObjectNode root = MAPPER.createObjectNode();
    usage.forEach((name, value) -> root.set(name, node(value)));
    return MAPPER.writeValueAsString(root);
  }

  private static ObjectNode node(SkillUsage value) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("lastUsedEpochMs", value.lastUsedEpochMs());
    node.put("useCount", value.useCount());
    node.put("state", value.state().name());
    node.put("pinned", value.pinned());
    SkillOrigin origin = value.origin();
    if (origin == null) {
      node.putNull("origin");
    } else {
      ObjectNode originNode = node.putObject("origin");
      originNode.put("source", origin.source());
      originNode.put("candidate", origin.candidate());
      originNode.put("installedEpochMs", origin.installedEpochMs());
    }
    return node;
  }

  private static SkillUsage usage(JsonNode node) {
    String state = node.path("state").asText(null);
    return new SkillUsage(
        node.path("lastUsedEpochMs").asLong(0L),
        node.path("useCount").asLong(0L),
        state == null ? SkillState.ACTIVE : SkillState.valueOf(state),
        node.path("pinned").asBoolean(false),
        origin(node.path("origin")));
  }

  private static SkillOrigin origin(JsonNode node) {
    if (!node.isObject()) {
      return null;
    }
    return new SkillOrigin(
        node.path("source").asText(null),
        node.path("candidate").asText(null),
        node.path("installedEpochMs").asLong(0L));
  }
}
