package dev.omatheusmesmo.qlawkus.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ChatMessageType;
import java.io.UncheckedIOException;

/** JSON shared by the working-memory backends. */
public final class ChatMessageJson {

    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private ChatMessageJson() {
    }

    public static String serialize(ChatMessage message) {
        return withType(ChatMessageSerializer.messageToJson(message), message.type());
    }

    /** Supplies the discriminator when LangChain4j's native writer omitted it. */
    public static String withType(String json, ChatMessageType type) {
        try {
            var node = (ObjectNode) JSON.readTree(json);
            return node.hasNonNull("type") ? json : node.put("type", type.name()).toString();
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Cannot read the chat message payload", e);
        }
    }
}
