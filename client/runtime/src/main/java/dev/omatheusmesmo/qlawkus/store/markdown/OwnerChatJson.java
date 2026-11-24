package dev.omatheusmesmo.qlawkus.store.markdown;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import java.time.Instant;

/**
 * Maps a stored owner chat field by field through Jackson's tree model, so no type is found by
 * reflection and a native image needs no registration for it. The layout is the one the reflective
 * mapper wrote: an ISO-8601 instant.
 */
final class OwnerChatJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OwnerChatJson() {
    }

    static String write(OwnerChat chat) throws JsonProcessingException {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("providerId", chat.providerId());
        node.put("chatId", chat.chatId());
        node.put("lastSeen", chat.lastSeen() == null ? null : chat.lastSeen().toString());
        return MAPPER.writeValueAsString(node);
    }

    static OwnerChat read(String json) throws JsonProcessingException {
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
