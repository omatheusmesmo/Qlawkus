package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import dev.omatheusmesmo.qlawkus.store.MessagesAppendedEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;

/**
 * Archives conversation transcripts into the embedding store (tagged {@code source=transcript}) so
 * the agent can semantically search past conversations (Hermes' {@code session_search}). Distinct
 * from extracted facts: this preserves what was actually said. Hash dedup makes re-archival cheap,
 * so it is safe even though the same message may arrive in several events.
 */
@ApplicationScoped
public class TranscriptArchiveObserver {

  @Inject
  FactStore factStore;

  @ConfigProperty(name = "qlawkus.agent.transcript-archive.enabled", defaultValue = "true")
  boolean enabled;

  void onMessagesAppended(@ObservesAsync MessagesAppendedEvent event) {
    if (!enabled) {
      return;
    }
    for (ChatMessage message : event.messages()) {
      if (message.type() != ChatMessageType.USER && message.type() != ChatMessageType.AI) {
        continue;
      }
      String text = ConversationFormatter.format(List.of(message)).trim();
      if (text.isBlank()) {
        continue;
      }
      try {
        factStore.store(text, Map.of(
            "source", MemorySource.TRANSCRIPT.value(),
            "memoryId", event.memoryId()));
      } catch (Exception e) {
        Log.warnf(e, "Failed to archive transcript message");
      }
    }
  }
}
