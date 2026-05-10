package dev.omatheusmesmo.qlawkus.it.cognition;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.cognition.SemanticExtractorObserver;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.store.pg.ChatMessageEntity;
import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.concurrent.TimeUnit.SECONDS;

@QuarkusTest
@Execution(ExecutionMode.SAME_THREAD)
class CognitionTest {

    @Inject
    AgentService agentService;

    @Inject
    WorkingMemoryStore memoryStore;

    @Inject
    EmbeddingRepository embeddingRepository;

    @Inject
    SemanticExtractorObserver observer;

    @BeforeEach
    void rateLimitPause() {
    }

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

        observer.extractAndStore(messages);

        await("semantic fact extracted via CDI event")
            .atMost(300, SECONDS)
                .pollDelay(3, SECONDS)
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
            .atMost(120, SECONDS)
            .pollInterval(2, SECONDS)
            .until(() -> embeddingCountBySource("semantic-extractor") > 0);

        memoryStore.deleteMessages("default");

        String response = agentService
                .chat("Based on my preferences, write a simple Java method that adds two integers and returns the result.")
                .collect()
                .in(StringBuilder::new, StringBuilder::append)
                .await()
                .atMost(Duration.ofSeconds(300))
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
            .atMost(120, SECONDS)
            .pollInterval(2, SECONDS)
            .until(() -> embeddingCountBySource("semantic-extractor") > 0);

        long count = embeddingCountBySource("semantic-extractor");
        assertTrue(count > 0, "Should have stored at least one fact about var preference");
    }

    @Test
    void llm_usesSearchMemories_toolToRecallPreference() {
        List<ChatMessage> seedMessages = List.of(
                UserMessage.from("My favorite programming language is Rust."),
                AiMessage.from("Got it! I'll remember you prefer Rust.")
        );

        observer.extractAndStore(seedMessages);

        await("semantic fact stored for search test")
            .atMost(120, SECONDS)
                .pollInterval(2, SECONDS)
                .until(() -> embeddingCountBySource("semantic-extractor") > 0);

        memoryStore.deleteMessages("default");

        String response = agentService
                .chat("Search your memories for my favorite programming language. What is it? Use the searchMemories tool.")
                .collect()
                .in(StringBuilder::new, StringBuilder::append)
                .await()
                .atMost(Duration.ofSeconds(300))
                .toString();

        assertFalse(response.isBlank());
        assertTrue(response.toLowerCase().contains("rust"),
                "Agent should recall 'Rust' from memories via searchMemories tool. Got: " + response);
    }

    @Transactional
    long embeddingCountBySource(String source) {
        return embeddingRepository.countBySource(source);
    }
}
