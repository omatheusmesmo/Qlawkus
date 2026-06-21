package dev.omatheusmesmo.qlawkus.it.markdown;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownEpisodicStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownFactStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownWorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.testing.QlawkusTestUtils;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the database-free distribution: with qlawkus-cognition-pgvector absent from the classpath the
 * agent boots on the markdown stores and persists facts, working memory and journals to files only.
 */
@QuarkusTest
@ConnectWireMock
class MarkdownOnlyTest {

    WireMock wiremock;

    @Inject
    AgentService agentService;

    @Inject
    FactStore factStore;

    @Inject
    WorkingMemoryStore workingMemoryStore;

    @Inject
    EpisodicStore episodicStore;

    @BeforeEach
    void setupStubs() {
        QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
    }

    @Test
    void boots_onMarkdownStores_withNoDatabaseOnClasspath() {
        assertNotNull(agentService, "AgentService should boot with only qlawkus-client on the classpath");
        assertInstanceOf(MarkdownFactStore.class, factStore);
        assertInstanceOf(MarkdownWorkingMemoryStore.class, workingMemoryStore);
        assertInstanceOf(MarkdownEpisodicStore.class, episodicStore);

        assertFalse(classPresent("io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore"),
                "pgvector must not be on the classpath in the database-free distribution");
        assertFalse(classPresent("io.agroal.api.AgroalDataSource"),
                "no JDBC datasource must be on the classpath in the database-free distribution");
    }

    @Test
    void workingMemory_persistsToJsonlFile() {
        String memoryId = "markdown-only-wm";
        workingMemoryStore.updateMessages(memoryId,
                List.of(UserMessage.from("ping"), AiMessage.from("pong")));

        Path file = Path.of("target/markdown-only/working-memory", memoryId + ".jsonl");
        assertTrue(Files.isRegularFile(file), "working memory should be persisted to " + file);
    }

    @Test
    void journal_persistsToDatedMarkdownFile() {
        LocalDate date = LocalDate.of(2026, 6, 21);
        episodicStore.storeEpisode(date, "A quiet day of refactoring.", 4);

        assertTrue(episodicStore.existsForDate(date), "journal should exist for the stored date");
        Path file = Path.of("target/markdown-only/journals", date + ".md");
        assertTrue(Files.isRegularFile(file), "journal should be persisted to " + file);
    }

    @Test
    void chat_roundTrips_andPersistsWorkingMemoryToFile() {
        String response = agentService.chat("markdown-only-chat", "Say exactly: pong")
                .collect().asList()
                .await().atMost(Duration.ofMinutes(2))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .reduce("", (a, b) -> a + b);

        assertThat(response, QlawkusTestUtils.containsStringOrMock("pong"));
        Path file = Path.of("target/markdown-only/working-memory", "markdown-only-chat.jsonl");
        assertTrue(Files.isRegularFile(file), "chat turn should be persisted to working memory file");
    }

    private static boolean classPresent(String fqn) {
        try {
            Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
