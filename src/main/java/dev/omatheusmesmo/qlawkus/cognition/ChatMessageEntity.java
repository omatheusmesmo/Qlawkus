package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ChatMessageType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import java.time.LocalDateTime;
import java.util.List;

@Entity
public class ChatMessageEntity extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public String memoryId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  public ChatMessageType type;

  @Column(columnDefinition = "TEXT", nullable = false)
  public String content;

  @Column(nullable = false)
  public LocalDateTime createdAt;

  public static List<ChatMessageEntity> findByMemoryIdOrdered(String memoryId) {
    return list("memoryId = ?1 order by createdAt", memoryId);
  }

  public static long deleteByMemoryId(String memoryId) {
    return delete("memoryId = ?1", memoryId);
  }

  @PrePersist
  void persistCreatedAt() {
    createdAt = LocalDateTime.now();
  }

  public static ChatMessageEntity fromChatMessage(String memoryId, ChatMessage message) {
    ChatMessageEntity entity = new ChatMessageEntity();
    entity.memoryId = memoryId;
    entity.type = message.type();
    entity.content = ChatMessageSerializer.messageToJson(message);
    return entity;
  }

  public ChatMessage toChatMessage() {
    return ChatMessageDeserializer.messageFromJson(content);
  }
}
