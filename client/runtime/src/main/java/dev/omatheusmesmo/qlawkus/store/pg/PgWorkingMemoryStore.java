package dev.omatheusmesmo.qlawkus.store.pg;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
public class PgWorkingMemoryStore implements WorkingMemoryStore, ChatMemoryStore {

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
    ChatMessageEntity.deleteByMemoryId(memoryId);
    for (ChatMessage message : messages) {
      ChatMessageEntity.fromChatMessage(memoryId, message).persist();
    }
  }

  @Override
  @Transactional
  public void deleteMessages(String memoryId) {
    ChatMessageEntity.deleteByMemoryId(memoryId);
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
