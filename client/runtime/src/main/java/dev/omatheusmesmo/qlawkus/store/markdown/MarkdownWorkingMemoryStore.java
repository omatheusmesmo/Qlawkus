package dev.omatheusmesmo.qlawkus.store.markdown;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.MessagesAppendedEvent;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownWorkingMemoryFiles.StoredMessage;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Markdown-backed {@link WorkingMemoryStore} (and langchain4j {@link ChatMemoryStore}). Each
 * conversation is an append-only {@code <memoryId>.jsonl} log under
 * {@code qlawkus.agent.working-memory.root}; no database. The 40-message window is applied by
 * langchain4j on read, not here.
 *
 * <p>This is the {@code @DefaultBean} working-memory store, so it backs the agent whenever no
 * Postgres backend is on the classpath (the database-free distribution). When the
 * qlawkus-cognition-pgvector extension is present its non-default {@code PgWorkingMemoryStore} /
 * {@code HybridWorkingMemoryStore} override this one. For the default to apply to the
 * {@link ChatMemoryStore} type, {@code ClientProcessor} vetoes the upstream
 * {@code @DefaultBean InMemoryChatMemoryStore} so there is a single default.
 */
@ApplicationScoped
@DefaultBean
public class MarkdownWorkingMemoryStore implements WorkingMemoryStore, ChatMemoryStore {

  private final MarkdownWorkingMemoryFiles files;
  private final Event<MessagesAppendedEvent> messagesAppended;

  @Inject
  public MarkdownWorkingMemoryStore(AgentConfig config, Event<MessagesAppendedEvent> messagesAppended) {
    this(config.workingMemory().root(), messagesAppended);
  }

  public MarkdownWorkingMemoryStore(String root, Event<MessagesAppendedEvent> messagesAppended) {
    this.files = new MarkdownWorkingMemoryFiles(Path.of(root));
    this.messagesAppended = messagesAppended;
  }

  @Override
  public List<ChatMessage> getMessages(String memoryId) {
    return files.read(memoryId).stream()
        .map(StoredMessage::messageJson)
        .map(ChatMessageDeserializer::messageFromJson)
        .toList();
  }

  @Override
  public void updateMessages(String memoryId, List<ChatMessage> messages) {
    List<StoredMessage> existing = files.read(memoryId);
    List<String> existingJson = existing.stream().map(StoredMessage::messageJson).toList();
    List<String> incomingJson = messages.stream().map(ChatMessageSerializer::messageToJson).toList();

    List<ChatMessage> appended;
    if (isAppendOf(existingJson, incomingJson)) {
      List<String> tail = incomingJson.subList(existingJson.size(), incomingJson.size());
      if (!tail.isEmpty()) {
        files.append(memoryId, tail);
      }
      appended = messages.subList(existingJson.size(), messages.size());
    } else {
      files.replace(memoryId, incomingJson);
      appended = messages.stream()
          .filter(m -> !existingJson.contains(ChatMessageSerializer.messageToJson(m)))
          .toList();
    }

    if (!appended.isEmpty() && messagesAppended != null) {
      messagesAppended.fireAsync(new MessagesAppendedEvent(memoryId, appended));
    }
  }

  /**
   * True when {@code incoming} is the already-stored history plus new trailing messages, mirroring
   * {@code PgWorkingMemoryStore.isAppendOf}. Lets the common case append only the new messages and
   * keep earlier timestamps intact; a divergence (window eviction) falls back to a full rewrite.
   */
  private static boolean isAppendOf(List<String> existing, List<String> incoming) {
    if (incoming.size() < existing.size()) {
      return false;
    }
    for (int i = 0; i < existing.size(); i++) {
      if (!existing.get(i).equals(incoming.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void deleteMessages(String memoryId) {
    files.delete(memoryId);
  }

  @Override
  public Instant lastActivity(String memoryId) {
    List<StoredMessage> stored = files.read(memoryId);
    return stored.isEmpty() ? null : stored.get(stored.size() - 1).createdAt();
  }

  @Override
  public List<ChatMessage> findByDateRange(LocalDate date) {
    Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    return files.readAll().stream()
        .filter(m -> !m.createdAt().isBefore(start) && m.createdAt().isBefore(end))
        .map(StoredMessage::messageJson)
        .map(ChatMessageDeserializer::messageFromJson)
        .toList();
  }

  @Override
  public long count() {
    return files.count();
  }

  @Override
  public void purgeAll() {
    files.deleteAll();
  }

  @Override
  public List<ChatMessage> getMessages(Object memoryId) {
    return getMessages(memoryId.toString());
  }

  @Override
  public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    updateMessages(memoryId.toString(), messages);
  }

  @Override
  public void deleteMessages(Object memoryId) {
    deleteMessages(memoryId.toString());
  }
}
