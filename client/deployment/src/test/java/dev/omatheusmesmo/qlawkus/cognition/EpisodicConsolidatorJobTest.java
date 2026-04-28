package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.pg.ChatMessageEntity;
import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingRepository;
import dev.omatheusmesmo.qlawkus.store.pg.Journal;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Tag("slow")
@QuarkusTest
class EpisodicConsolidatorJobTest {

  @InjectMock
  ChatModel chatModel;

  @Inject
  EpisodicConsolidatorJob job;

  @Inject
  EpisodicStore episodicStore;

  @Inject
  EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    SearchMemoriesTool searchMemoriesTool;

    @Inject
    EmbeddingRepository embeddingRepository;

    @Inject
    Scheduler scheduler;

    @AfterEach
    @Transactional
    void cleanup() {
        Journal.deleteAll();
        ChatMessageEntity.deleteAll();
        embeddingRepository.deleteAll();
    }

  @Transactional
  void seedMessages(String... texts) {
    for (String text : texts) {
      ChatMessageEntity.fromChatMessage("session-1", new UserMessage(text)).persist();
    }
  }

  @Transactional
  Journal findJournal(LocalDate date) {
    return Journal.findByDate(date);
  }

  @Test
  void consolidateDate_createsJournalFromMessages() {
    seedMessages("I prefer dark theme", "Tell me about dark theme");

    when(chatModel.chat(anyString())).thenReturn("User expressed preference for dark theme.");

    job.consolidateDate(LocalDate.now(ZoneOffset.UTC));

    Journal journal = findJournal(LocalDate.now(ZoneOffset.UTC));
    assertNotNull(journal);
    assertTrue(journal.summary.contains("dark theme"));
    assertEquals(2, journal.messageCount);
  }

  @Test
  void consolidateDate_skipsWhenNoMessages() {
    job.consolidateDate(LocalDate.of(2020, 1, 1));

    assertEquals(0, episodicStore.count());
  }

  @Test
  @Transactional
  void consolidateDate_skipsWhenJournalAlreadyExists() {
    Journal existing = new Journal();
    existing.date = LocalDate.now(ZoneOffset.UTC);
    existing.summary = "Existing summary";
    existing.messageCount = 5;
    existing.persist();

    ChatMessageEntity.fromChatMessage("session-1", new UserMessage("hello")).persist();

    when(chatModel.chat(anyString())).thenReturn("New summary.");

    job.consolidateDate(LocalDate.now(ZoneOffset.UTC));

    Journal journal = Journal.findByDate(LocalDate.now(ZoneOffset.UTC));
    assertEquals("Existing summary", journal.summary);
    assertEquals(5, journal.messageCount);
  }

  @Test
  void consolidateDate_handlesLlmFailure() {
    seedMessages("hello");

    when(chatModel.chat(anyString())).thenThrow(new RuntimeException("LLM unavailable"));

    job.consolidateDate(LocalDate.now(ZoneOffset.UTC));

    Journal journal = findJournal(LocalDate.now(ZoneOffset.UTC));
    assertNotNull(journal);
    assertTrue(journal.summary.contains("Consolidation failed"));
  }

  @Test
  void summarizeMessages_returnsSummary() {
    when(chatModel.chat(anyString())).thenReturn("Summarized conversation.");

    List<dev.langchain4j.data.message.ChatMessage> messages = java.util.List.of(new UserMessage("test message"));

    String summary = job.summarizeMessages(messages, LocalDate.now(ZoneOffset.UTC));

    assertEquals("Summarized conversation.", summary);
  }

  @Test
  void consolidateDate_embedsSummaryIntoVectorStore() {
    seedMessages("I prefer dark theme");

    when(chatModel.chat(anyString())).thenReturn("User expressed preference for dark theme.");

    job.consolidateDate(LocalDate.now(ZoneOffset.UTC));

    Embedding queryEmbedding = embeddingModel.embed("dark theme preference").content();
    EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
        EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(5)
            .minScore(0.5)
            .build()
    );

    boolean found = results.matches().stream()
        .map(EmbeddingMatch::embedded)
        .map(TextSegment::text)
        .anyMatch(text -> text.contains("dark theme"));

    assertTrue(found, "Expected to find journal summary with 'dark theme' in vector store");
  }

  @Test
  void consolidateDate_storesEpisodicMetadataInEmbedding() {
    seedMessages("hello");

    when(chatModel.chat(anyString())).thenReturn("User greeted the agent.");

    job.consolidateDate(LocalDate.now(ZoneOffset.UTC));

    Embedding queryEmbedding = embeddingModel.embed("user greeting").content();
    EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
        EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(5)
            .minScore(0.5)
            .build()
    );

    boolean hasSource = results.matches().stream()
        .anyMatch(m -> "episodic-consolidator".equals(m.embedded().metadata().getString("source")));

        assertTrue(hasSource, "Expected metadata source=episodic-consolidator");
    }

    @Test
    void scheduler_registersConsolidatorJob() {
        List<Trigger> triggers = scheduler.getScheduledJobs();
        Optional<Trigger> consolidatorJob = triggers.stream()
                .filter(t -> t.getId() != null && t.getId().contains("EpisodicConsolidatorJob"))
                .findFirst();

        assertTrue(consolidatorJob.isPresent(),
                "Expected EpisodicConsolidatorJob to be registered in the scheduler");
    }

    @Test
    void consolidatedSummary_isRetrievableViaSearchMemoriesTool() {
        seedMessages("I always deploy on Fridays, it's my tradition");

        when(chatModel.chat(anyString())).thenReturn(
                "User has a tradition of deploying on Fridays despite common advice against it.");

        job.consolidateDate(LocalDate.now(ZoneOffset.UTC));

        List<String> results = searchMemoriesTool.searchMemories("deployment policy Friday");
        assertFalse(results.isEmpty(), "SearchMemoriesTool should find consolidated journal summary");
        assertTrue(results.stream().anyMatch(f -> f.toLowerCase().contains("friday") || f.toLowerCase().contains("deploy")),
                "Expected result about Friday deployment tradition, got: " + results);
    }
}
