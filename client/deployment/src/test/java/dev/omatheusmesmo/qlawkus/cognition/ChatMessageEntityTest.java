package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.omatheusmesmo.qlawkus.store.pg.ChatMessageEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ChatMessageEntityTest {

  @Test
  @Transactional
  void fromChatMessage_serializesUserMessage() {
    ChatMessageEntity entity = ChatMessageEntity.fromChatMessage("test-session", new UserMessage("hello"));

    assertEquals("test-session", entity.memoryId);
    assertEquals(ChatMessageType.USER, entity.type);
    assertNotNull(entity.content);
  }

  @Test
  @Transactional
  void fromChatMessage_serializesAiMessage() {
    ChatMessageEntity entity = ChatMessageEntity.fromChatMessage("test-session", AiMessage.from("hi there"));

    assertEquals(ChatMessageType.AI, entity.type);
  }

  @Test
  @Transactional
  void toChatMessage_roundtripUserMessage() {
    ChatMessageEntity entity = ChatMessageEntity.fromChatMessage("test-session", new UserMessage("hello"));
    entity.persist();

    ChatMessageEntity persisted = ChatMessageEntity.findById(entity.id);
    UserMessage deserialized = (UserMessage) persisted.toChatMessage();

    assertEquals("hello", deserialized.singleText());
  }

  @Test
  @Transactional
  void toChatMessage_roundtripAiMessage() {
    ChatMessageEntity entity = ChatMessageEntity.fromChatMessage("test-session", AiMessage.from("response"));
    entity.persist();

    ChatMessageEntity persisted = ChatMessageEntity.findById(entity.id);
    AiMessage deserialized = (AiMessage) persisted.toChatMessage();

    assertEquals("response", deserialized.text());
  }

  @Test
  @Transactional
  void findByMemoryIdOrdered_returnsInOrder() {
    ChatMessageEntity.fromChatMessage("session-1", new UserMessage("first")).persist();
    ChatMessageEntity.fromChatMessage("session-1", AiMessage.from("second")).persist();
    ChatMessageEntity.fromChatMessage("session-2", new UserMessage("other")).persist();

    var messages = ChatMessageEntity.findByMemoryIdOrdered("session-1");

    assertEquals(2, messages.size());
    assertEquals(ChatMessageType.USER, messages.get(0).type);
    assertEquals(ChatMessageType.AI, messages.get(1).type);
  }

  @Test
  @Transactional
  void deleteByMemoryId_removesOnlyTargetSession() {
    ChatMessageEntity.fromChatMessage("session-a", new UserMessage("a")).persist();
    ChatMessageEntity.fromChatMessage("session-b", new UserMessage("b")).persist();

    ChatMessageEntity.deleteByMemoryId("session-a");

    assertEquals(0, ChatMessageEntity.findByMemoryIdOrdered("session-a").size());
    assertEquals(1, ChatMessageEntity.findByMemoryIdOrdered("session-b").size());
  }
}
