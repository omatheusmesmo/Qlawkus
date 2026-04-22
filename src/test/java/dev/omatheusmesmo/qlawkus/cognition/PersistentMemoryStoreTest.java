package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PersistentMemoryStoreTest {

  @Inject
  PersistentMemoryStore store;

  @Test
  @Transactional
  void getMessages_returnsEmptyForNewSession() {
    var messages = store.getMessages("nonexistent-session");

    assertTrue(messages.isEmpty());
  }

  @Test
  @Transactional
  void updateMessages_persistsAndRetrievesMessages() {
    String sessionId = "test-session-1";
    store.updateMessages(sessionId, List.of(
        new UserMessage("hello"),
        AiMessage.from("hi there")
    ));

    List<ChatMessage> retrieved = store.getMessages(sessionId);

    assertEquals(2, retrieved.size());
    assertEquals(UserMessage.class, retrieved.get(0).getClass());
    assertEquals(AiMessage.class, retrieved.get(1).getClass());
  }

  @Test
  @Transactional
  void updateMessages_replacesExistingMessages() {
    String sessionId = "test-session-2";
    store.updateMessages(sessionId, List.of(
        new UserMessage("first"),
        AiMessage.from("first response")
    ));
    store.updateMessages(sessionId, List.of(
        new UserMessage("second"),
        AiMessage.from("second response")
    ));

    List<ChatMessage> retrieved = store.getMessages(sessionId);

    assertEquals(2, retrieved.size());
    assertEquals("second", ((UserMessage) retrieved.get(0)).singleText());
  }

  @Test
  @Transactional
  void deleteMessages_removesSession() {
    String sessionId = "test-session-3";
    store.updateMessages(sessionId, List.of(new UserMessage("to be deleted")));

    store.deleteMessages(sessionId);

    assertTrue(store.getMessages(sessionId).isEmpty());
  }

  @Test
  @Transactional
  void multipleSessions_areIsolated() {
    store.updateMessages("session-a", List.of(new UserMessage("a")));
    store.updateMessages("session-b", List.of(new UserMessage("b"), AiMessage.from("b response")));

    assertEquals(1, store.getMessages("session-a").size());
    assertEquals(2, store.getMessages("session-b").size());
  }
}
