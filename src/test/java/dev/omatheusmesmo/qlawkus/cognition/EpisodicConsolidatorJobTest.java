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
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class EpisodicConsolidatorJobTest {

  @InjectMock
  ChatModel chatModel;

  @Inject
  EpisodicConsolidatorJob job;

  @Inject
  EmbeddingModel embeddingModel;

  @Inject
  EmbeddingStore<TextSegment> embeddingStore;

  @AfterEach
  @Transactional
  void cleanup() {
    Journal.deleteAll();
    ChatMessageEntity.deleteAll();
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

    job.consolidateDate(LocalDate.now());

    Journal journal = findJournal(LocalDate.now());
    assertNotNull(journal);
    assertTrue(journal.summary.contains("dark theme"));
    assertEquals(2, journal.messageCount);
  }

  @Test
  void consolidateDate_skipsWhenNoMessages() {
    job.consolidateDate(LocalDate.of(2020, 1, 1));

    assertEquals(0, Journal.count());
  }

  @Test
  @Transactional
  void consolidateDate_skipsWhenJournalAlreadyExists() {
    Journal existing = new Journal();
    existing.date = LocalDate.now();
    existing.summary = "Existing summary";
    existing.messageCount = 5;
    existing.persist();

    ChatMessageEntity.fromChatMessage("session-1", new UserMessage("hello")).persist();

    when(chatModel.chat(anyString())).thenReturn("New summary.");

    job.consolidateDate(LocalDate.now());

    Journal journal = Journal.findByDate(LocalDate.now());
    assertEquals("Existing summary", journal.summary);
    assertEquals(5, journal.messageCount);
  }

  @Test
  void consolidateDate_handlesLlmFailure() {
    seedMessages("hello");

    when(chatModel.chat(anyString())).thenThrow(new RuntimeException("LLM unavailable"));

    job.consolidateDate(LocalDate.now());

    Journal journal = findJournal(LocalDate.now());
    assertNotNull(journal);
    assertTrue(journal.summary.contains("Consolidation failed"));
  }

  @Test
  void summarizeMessages_returnsSummary() {
    when(chatModel.chat(anyString())).thenReturn("Summarized conversation.");

    ChatMessageEntity entity = ChatMessageEntity.fromChatMessage("session-1", new UserMessage("test message"));

    String summary = job.summarizeMessages(java.util.List.of(entity), LocalDate.now());

    assertEquals("Summarized conversation.", summary);
  }

  @Test
  void consolidateDate_embedsSummaryIntoVectorStore() {
    seedMessages("I prefer dark theme");

    when(chatModel.chat(anyString())).thenReturn("User expressed preference for dark theme.");

    job.consolidateDate(LocalDate.now());

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

    job.consolidateDate(LocalDate.now());

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
}
