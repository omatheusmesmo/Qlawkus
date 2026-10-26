package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.util.Map;

@ApplicationScoped
public class SemanticExtractorObserver {

  @Inject
  ChatModel chatModel;

  @Inject
  FactStore factStore;

  @Inject
  AgentConfig agentConfig;

  void onChatCompleted(@ObservesAsync ChatCompletedEvent event) {
    if (!agentConfig.semanticExtractor().enabled()) return;
    if (event.messages().isEmpty()) return;

    extractAndStore(event.messages());
  }

  public void extractAndStore(Iterable<ChatMessage> messages) {
    try {
      String conversation = ConversationFormatter.format(messages);

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

      String response = WorkloadContext.callAs(WorkloadContext.BATCH,
          () -> chatModel.chat(extractionPrompt));
      if (response == null || response.isBlank()) return;

      int stored = 0;
      int failed = 0;
      for (String line : response.split("\n")) {
        String fact = line.trim().replaceAll("^-\\s*", "");
        if (fact.isEmpty()) continue;

        try {
          factStore.store(fact, Map.of("source", MemorySource.SEMANTIC_EXTRACTOR.value()));
          stored++;
          Log.debugf("Semantic fact extracted: %s", fact);
        } catch (Exception e) {
          failed++;
          Log.warnf("Failed to store a semantic fact (%s)", e.getClass().getSimpleName());
          Log.debugf(e, "Failed to store semantic fact: %s", fact);
        }
      }
      Log.infof("Semantic extraction: %d facts stored, %d failed", stored, failed);
    } catch (Exception e) {
      Log.errorf(e, "Failed to extract semantic facts");
    }
  }
}
