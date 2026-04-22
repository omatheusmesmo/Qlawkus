package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class PersistentMemoryStore implements ChatMemoryStore {

  @Override
  @Transactional
  public List<ChatMessage> getMessages(Object memoryId) {
    return ChatMessageEntity.findByMemoryIdOrdered(memoryId.toString())
        .stream()
        .map(ChatMessageEntity::toChatMessage)
        .toList();
  }

  @Override
  @Transactional
  public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    String id = memoryId.toString();
    ChatMessageEntity.deleteByMemoryId(id);
    for (ChatMessage message : messages) {
      ChatMessageEntity.fromChatMessage(id, message).persist();
    }
  }

  @Override
  @Transactional
  public void deleteMessages(Object memoryId) {
    ChatMessageEntity.deleteByMemoryId(memoryId.toString());
  }
}
