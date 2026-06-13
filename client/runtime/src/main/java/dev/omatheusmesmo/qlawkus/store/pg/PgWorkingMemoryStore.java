package dev.omatheusmesmo.qlawkus.store.pg;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.omatheusmesmo.qlawkus.store.MessagesAppendedEvent;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class PgWorkingMemoryStore implements WorkingMemoryStore, ChatMemoryStore {

  @Inject
  Event<MessagesAppendedEvent> messagesAppended;

  @Override
  @Transactional
  public List<ChatMessage> getMessages(String memoryId) {
    return ChatMessageEntity.findByMemoryIdOrdered(memoryId)
      .stream()
      .map(ChatMessageEntity::toChatMessage)
      .toList();
  }

  @Override
  @Transactional
  public void updateMessages(String memoryId, List<ChatMessage> messages) {
    List<ChatMessageEntity> existing = ChatMessageEntity.findByMemoryIdOrdered(memoryId);
    Set<String> existingJson = existing.stream().map(e -> e.content).collect(Collectors.toSet());

    if (isAppendOf(existing, messages)) {
      for (ChatMessage message : messages.subList(existing.size(), messages.size())) {
        ChatMessageEntity.fromChatMessage(memoryId, message).persist();
      }
    } else {
      ChatMessageEntity.deleteByMemoryId(memoryId);
      for (ChatMessage message : messages) {
        ChatMessageEntity.fromChatMessage(memoryId, message).persist();
      }
    }

    List<ChatMessage> appended = messages.stream()
        .filter(m -> !existingJson.contains(ChatMessageSerializer.messageToJson(m)))
        .toList();
    if (!appended.isEmpty()) {
      messagesAppended.fireAsync(new MessagesAppendedEvent(memoryId, appended));
    }
  }

  /**
   * True when {@code incoming} is the already-stored history plus new trailing messages.
   * In that common case only the new messages are persisted, preserving the original
   * {@code createdAt} of existing rows (which the episodic consolidator relies on) and
   * avoiding a full rewrite every turn. A divergence (e.g. memory-window eviction) falls
   * back to a full replace.
   */
  private static boolean isAppendOf(List<ChatMessageEntity> existing, List<ChatMessage> incoming) {
    if (incoming.size() < existing.size()) {
      return false;
    }
    for (int i = 0; i < existing.size(); i++) {
      String incomingJson = ChatMessageSerializer.messageToJson(incoming.get(i));
      if (!existing.get(i).content.equals(incomingJson)) {
        return false;
      }
    }
    return true;
  }

  @Override
  @Transactional
  public void deleteMessages(String memoryId) {
    ChatMessageEntity.deleteByMemoryId(memoryId);
  }

  @Override
  @Transactional
  public Instant lastActivity(String memoryId) {
    return ChatMessageEntity.findLatestTimestamp(memoryId);
  }

  @Override
  @Transactional
  public List<ChatMessage> findByDateRange(LocalDate date) {
    return ChatMessageEntity.findByDateRange(date)
      .stream()
      .map(ChatMessageEntity::toChatMessage)
      .toList();
  }

  @Override
  @Transactional
  public long count() {
    return ChatMessageEntity.count();
  }

  @Override
  @Transactional
  public void purgeAll() {
    ChatMessageEntity.deleteAll();
  }

  @Override
  @Transactional
  public List<ChatMessage> getMessages(Object memoryId) {
    return getMessages(memoryId.toString());
  }

  @Override
  @Transactional
  public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    updateMessages(memoryId.toString(), messages);
  }

  @Override
  @Transactional
  public void deleteMessages(Object memoryId) {
    deleteMessages(memoryId.toString());
  }
}
