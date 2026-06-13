package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Renders chat messages as plain {@code TYPE: text} lines for LLM prompts. Using the real
 * textual content avoids leaking {@code ChatMessage.toString()} structure (e.g.
 * {@code UserMessage { contents = [TextContent { text = "..." }] }}) into the extraction and
 * consolidation prompts, which degraded fact quality.
 */
final class ConversationFormatter {

  private ConversationFormatter() {
  }

  static String format(Iterable<ChatMessage> messages) {
    StringBuilder conversation = new StringBuilder();
    for (ChatMessage message : messages) {
      conversation.append(message.type().name()).append(": ").append(text(message)).append("\n");
    }
    return conversation.toString();
  }

  private static String text(ChatMessage message) {
    return switch (message) {
      case UserMessage user -> user.hasSingleText() ? user.singleText() : joinText(user.contents());
      case AiMessage ai -> ai.text() != null ? ai.text() : "";
      case SystemMessage system -> system.text();
      case ToolExecutionResultMessage tool -> tool.text();
      default -> message.toString();
    };
  }

  private static String joinText(java.util.List<dev.langchain4j.data.message.Content> contents) {
    StringBuilder sb = new StringBuilder();
    for (var content : contents) {
      if (content instanceof TextContent textContent) {
        if (!sb.isEmpty()) {
          sb.append(' ');
        }
        sb.append(textContent.text());
      }
    }
    return sb.toString();
  }
}
