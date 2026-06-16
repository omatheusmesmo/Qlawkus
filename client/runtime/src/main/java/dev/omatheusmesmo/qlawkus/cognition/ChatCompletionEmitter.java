package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.omatheusmesmo.qlawkus.store.MessagesAppendedEvent;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Bridges the store-level {@link MessagesAppendedEvent} (fired on every channel) to the
 * turn-level {@link ChatCompletedEvent}, so post-turn consumers (semantic extraction, brag
 * achievements) run regardless of entry point - REST, Discord or Telegram. Replaces the
 * previous REST-only emission that depended on the SSE stream completing.
 *
 * <p>A turn is considered complete when a final assistant message is appended. Intermediate
 * assistant messages that carry tool-execution requests are skipped, so the event fires once
 * per user turn rather than once per tool round.
 */
@ApplicationScoped
public class ChatCompletionEmitter {

  @Inject
  WorkingMemoryStore workingMemoryStore;

  @Inject
  Event<ChatCompletedEvent> chatCompleted;

  void onMessagesAppended(@ObservesAsync MessagesAppendedEvent event) {
    if (!isTurnComplete(event.messages())) {
      return;
    }
    List<ChatMessage> window = workingMemoryStore.getMessages(event.memoryId());
    if (window.isEmpty()) {
      return;
    }
    Log.infof("Firing ChatCompletedEvent with %d messages from memoryId=%s",
        window.size(), event.memoryId());
    chatCompleted.fireAsync(new ChatCompletedEvent(window));
  }

  static boolean isTurnComplete(List<ChatMessage> appended) {
    return appended.stream().anyMatch(message ->
        message instanceof AiMessage ai && !ai.hasToolExecutionRequests());
  }
}
