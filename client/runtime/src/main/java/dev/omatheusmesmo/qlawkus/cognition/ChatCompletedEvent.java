package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

public record ChatCompletedEvent(List<ChatMessage> messages) {
}
