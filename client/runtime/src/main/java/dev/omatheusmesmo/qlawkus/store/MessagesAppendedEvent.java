package dev.omatheusmesmo.qlawkus.store;

import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

/**
 * Fired (async) when new messages are appended to working memory, on any channel. Lets observers
 * archive transcripts without coupling to the chat entry points.
 */
public record MessagesAppendedEvent(String memoryId, List<ChatMessage> messages) {
}
