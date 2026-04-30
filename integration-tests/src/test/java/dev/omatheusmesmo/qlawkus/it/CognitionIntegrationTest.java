package dev.omatheusmesmo.qlawkus.it;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.cognition.ChatCompletedEvent;
import dev.omatheusmesmo.qlawkus.cognition.SemanticExtractorObserver;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.store.pg.ChatMessageEntity;
import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.concurrent.TimeUnit.SECONDS;

@QuarkusTest
@Execution(ExecutionMode.SAME_THREAD)
class CognitionIntegrationTest {

  @Inject
  AgentService agentService;

  @Inject
  ChatModel chatModel;

  @Inject
  WorkingMemoryStore memoryStore;

  @Inject
  EmbeddingRepository embeddingRepository;

  @Inject
  SemanticExtractorObserver observer;

  @Inject
  Event<ChatCompletedEvent> eventEmitter;

  @AfterEach
  @Transactional
  void cleanup() {
    embeddingRepository.deleteAll();
    ChatMessageEntity.deleteAll();
  }

  @Test
  void cdiEvent_triggersSemanticExtraction() {
    List<ChatMessage> messages = List.of(
      UserMessage.from("I always use IntelliJ IDEA for development"),
      AiMessage.from("Good choice! IntelliJ is a powerful IDE.")
    );

    eventEmitter.fireAsync(new ChatCompletedEvent(messages));

    await("semantic fact extracted via CDI event")
      .atMost(60, SECONDS)
      .pollInterval(2, SECONDS)
      .until(() -> embeddingCountBySource("semantic-extractor") > 0);

    long count = embeddingCountBySource("semantic-extractor");
    assertTrue(count > 0, "CDI event should trigger fact extraction, expected at least 1 embedding");
  }

  @Test
  void agentRemembersUserPreferenceAcrossSessions() {
    List<ChatMessage> session1Messages = List.of(
      UserMessage.from("I really dislike the var keyword in Java. Always use explicit type declarations instead of var."),
      AiMessage.from("Noted! I'll remember you prefer explicit types over var in Java.")
    );

    observer.extractAndStore(session1Messages);

    await("semantic fact stored")
      .atMost(30, SECONDS)
      .pollInterval(2, SECONDS)
      .until(() -> embeddingCountBySource("semantic-extractor") > 0);

    memoryStore.deleteMessages("default");

    String response = agentService
      .chat("Based on my preferences, write a simple Java method that adds two integers and returns the result.")
      .collect()
      .in(StringBuilder::new, StringBuilder::append)
      .await()
      .atMost(java.time.Duration.ofSeconds(300))
      .toString();

    assertFalse(response.isBlank());
    assertTrue(response.contains("int"),
      "Agent should use explicit 'int' type. Response: " + response);
  }

  @Test
  void factExtraction_storesDislikeVarPreference() {
    List<ChatMessage> messages = List.of(
      UserMessage.from("I hate using var in Java. Explicit types are much better.")
    );

    observer.extractAndStore(messages);

    await("semantic fact stored")
      .atMost(30, SECONDS)
      .pollInterval(2, SECONDS)
      .until(() -> embeddingCountBySource("semantic-extractor") > 0);

    long count = embeddingCountBySource("semantic-extractor");
    assertTrue(count > 0, "Should have stored at least one fact about var preference");
  }

  @Transactional
  long embeddingCountBySource(String source) {
    return embeddingRepository.countBySource(source);
  }
}
