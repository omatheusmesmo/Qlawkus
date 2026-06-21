package dev.omatheusmesmo.qlawkus.store.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test for the markdown working-memory backend: an append-only JSONL log per conversation, no
 * database, no event bus (the {@code Event} is null here). Runs under {@code @QuarkusTest} only so
 * langchain4j's Quarkus JSON codec (used by {@code ChatMessageSerializer}) is initialized; the store
 * itself is instantiated directly against a temp dir. Verifies round-trip, append-vs-replace,
 * date-range slicing, count, and purge.
 */
@QuarkusTest
class MarkdownWorkingMemoryStoreTest {

  @TempDir
  Path tempDir;

  private MarkdownWorkingMemoryStore store() {
    return new MarkdownWorkingMemoryStore(tempDir.toString(), null);
  }

  @Test
  void appendsAndReadsBackMessagesInOrder() {
    MarkdownWorkingMemoryStore store = store();
    store.updateMessages("default", List.of(UserMessage.from("hi")));
    store.updateMessages("default", List.of(UserMessage.from("hi"), AiMessage.from("hello")));

    List<ChatMessage> messages = store.getMessages("default");

    assertEquals(2, messages.size());
    assertEquals("hi", ((UserMessage) messages.get(0)).singleText());
    assertEquals("hello", ((AiMessage) messages.get(1)).text());
  }

  @Test
  void appendOnlyPersistsNewTrailingMessages() {
    MarkdownWorkingMemoryStore store = store();
    store.updateMessages("c1", List.of(UserMessage.from("one")));
    store.updateMessages("c1", List.of(UserMessage.from("one"), AiMessage.from("two")));
    store.updateMessages("c1", List.of(UserMessage.from("one"), AiMessage.from("two"),
        UserMessage.from("three")));

    assertEquals(3, store.getMessages("c1").size());
    assertEquals(3, store.count());
  }

  @Test
  void divergentHistoryReplacesTheLog() {
    MarkdownWorkingMemoryStore store = store();
    store.updateMessages("c1", List.of(UserMessage.from("a"), AiMessage.from("b")));
    store.updateMessages("c1", List.of(UserMessage.from("different")));

    List<ChatMessage> messages = store.getMessages("c1");
    assertEquals(1, messages.size());
    assertEquals("different", ((UserMessage) messages.get(0)).singleText());
  }

  @Test
  void perConversationIsolationAndCount() {
    MarkdownWorkingMemoryStore store = store();
    store.updateMessages("c1", List.of(UserMessage.from("a")));
    store.updateMessages("c2", List.of(UserMessage.from("b"), AiMessage.from("c")));

    assertEquals(1, store.getMessages("c1").size());
    assertEquals(2, store.getMessages("c2").size());
    assertEquals(3, store.count());
  }

  @Test
  void findByDateRangeReturnsTodaysMessages() {
    MarkdownWorkingMemoryStore store = store();
    store.updateMessages("c1", List.of(UserMessage.from("today")));

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    List<ChatMessage> hits = store.findByDateRange(today);

    assertTrue(hits.stream().anyMatch(m -> m instanceof UserMessage u && u.singleText().equals("today")));
    assertTrue(store.findByDateRange(today.minusDays(5)).isEmpty());
  }

  @Test
  void lastActivityReflectsLatestAppend() {
    MarkdownWorkingMemoryStore store = store();
    assertNull(store.lastActivity("c1"));
    store.updateMessages("c1", List.of(UserMessage.from("a")));
    assertTrue(store.lastActivity("c1") != null);
  }

  @Test
  void deleteAndPurge() {
    MarkdownWorkingMemoryStore store = store();
    store.updateMessages("c1", List.of(UserMessage.from("a")));
    store.updateMessages("c2", List.of(UserMessage.from("b")));

    store.deleteMessages("c1");
    assertEquals(0, store.getMessages("c1").size());
    assertEquals(1, store.count());

    store.purgeAll();
    assertEquals(0, store.count());
  }
}
