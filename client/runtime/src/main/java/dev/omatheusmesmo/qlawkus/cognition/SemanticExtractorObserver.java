package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class SemanticExtractorObserver {

  @Inject
  ChatModel chatModel;

  @Inject
  EmbeddingService embeddingService;

  void onChatCompleted(@ObservesAsync ChatCompletedEvent event) {
    if (event.messages().isEmpty()) return;

    extractAndStore(event.messages());
  }

  public void extractAndStore(Iterable<ChatMessage> messages) {
    try {
      StringBuilder conversation = new StringBuilder();
      for (ChatMessage m : messages) {
        conversation.append(m.type().name()).append(": ").append(m).append("\n");
      }

      String extractionPrompt = """
Extract factual information and user preferences from this conversation.
Return each fact as a separate line prefixed with '- '. If no facts or preferences are present, return nothing.

Examples:
- User prefers dark theme in IDE
- User works with Java and Quarkus
- User's name is Matheus
- User dislikes var keyword in Java

Conversation:
%s""".formatted(conversation);

      String response = chatModel.chat(extractionPrompt);
      if (response == null || response.isBlank()) return;

      for (String line : response.split("\n")) {
        String fact = line.trim().replaceAll("^-\\s*", "");
        if (fact.isEmpty()) continue;

        Metadata metadata = new Metadata();
        metadata.put("source", "semantic-extractor");
        embeddingService.store(fact, metadata);
        Log.infof("Semantic fact extracted: %s", fact);
      }
    } catch (Exception e) {
      Log.warnf(e, "Failed to extract semantic facts");
    }
  }
}
