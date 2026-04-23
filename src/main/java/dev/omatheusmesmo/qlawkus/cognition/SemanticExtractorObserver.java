package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

@ApplicationScoped
public class SemanticExtractorObserver {

  @Inject
  ChatModel chatModel;

  @Inject
  EmbeddingModel embeddingModel;

  @Inject
  EmbeddingStore<TextSegment> embeddingStore;

  void onChatCompleted(@Observes(during = TransactionPhase.AFTER_COMPLETION) ChatCompletedEvent event) {
    if (event.messages().isEmpty()) return;

    Infrastructure.getDefaultWorkerPool()
        .submit(() -> extractAndStore(event.messages()));
  }

  void extractAndStore(Iterable<ChatMessage> messages) {
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
        TextSegment segment = TextSegment.from(fact, metadata);
        Embedding embedding = embeddingModel.embed(fact).content();
        embeddingStore.add(embedding, segment);
        Log.infof("Semantic fact extracted: %s", fact);
      }
    } catch (Exception e) {
      Log.warnf(e, "Failed to extract semantic facts");
    }
  }
}
