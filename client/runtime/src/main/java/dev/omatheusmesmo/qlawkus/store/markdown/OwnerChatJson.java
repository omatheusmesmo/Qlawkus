package dev.omatheusmesmo.qlawkus.store.markdown;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import java.time.Instant;

/**
 * Maps a stored owner chat field by field through Jackson's tree model, so no type is found by
 * reflection and a native image needs no registration for it. The layout is the one the reflective
 * mapper wrote: an ISO-8601 instant.
 */
final class OwnerChatJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private OwnerChatJson() {
    }

    static String write(OwnerChat chat) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("providerId", chat.providerId());
        node.put("chatId", chat.chatId());
        node.put("lastSeen", chat.lastSeen() == null ? null : chat.lastSeen().toString());
        return MAPPER.writeValueAsString(node);
    }

    static OwnerChat read(String json) {
        JsonNode node = MAPPER.readTree(json);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("not an owner chat record");
        }
        JsonNode lastSeen = node.path("lastSeen");
        return new OwnerChat(
                node.path("providerId").asText(null),
                node.path("chatId").asText(null),
                lastSeen.isTextual() ? Instant.parse(lastSeen.asText()) : null);
    }
}
