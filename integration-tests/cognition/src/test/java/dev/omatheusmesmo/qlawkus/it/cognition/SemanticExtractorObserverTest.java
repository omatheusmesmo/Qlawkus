package dev.omatheusmesmo.qlawkus.it.cognition;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.omatheusmesmo.qlawkus.cognition.SemanticExtractorObserver;
import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class SemanticExtractorObserverTest {

  @InjectMock
  ChatModel chatModel;

  @Inject
  SemanticExtractorObserver observer;

  @Inject
  EmbeddingModel embeddingModel;

  @Inject
  EmbeddingStore<TextSegment> embeddingStore;

  @Inject
  EmbeddingRepository embeddingRepository;

  @AfterEach
  @Transactional
  void cleanup() {
    embeddingRepository.deleteAll();
  }

  @Test
  void extractAndStore_storesFactsInVectorStore() {
    when(chatModel.chat(anyString())).thenReturn("- User prefers Vim over VS Code\n- User codes in Rust");

    List<ChatMessage> messages = List.of(
      new UserMessage("I love Vim, never switching to VS Code"),
      AiMessage.from("Noted! Vim is a great editor.")
    );

    observer.extractAndStore(messages);

    Embedding queryEmbedding = embeddingModel.embed("editor preference Vim VS Code").content();
    EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
      EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(10)
        .minScore(0.5)
        .build()
    );

    assertFactFound(results, "Vim");

    Embedding rustQuery = embeddingModel.embed("programming language Rust").content();
    EmbeddingSearchResult<TextSegment> rustResults = embeddingStore.search(
      EmbeddingSearchRequest.builder()
        .queryEmbedding(rustQuery)
        .maxResults(10)
        .minScore(0.5)
        .build()
    );

    assertFactFound(rustResults, "Rust");
  }

  @Test
  void extractAndStore_storesMetadataWithSource() {
    when(chatModel.chat(anyString())).thenReturn("- User's name is Matheus");

    List<ChatMessage> messages = List.of(
      new UserMessage("My name is Matheus"),
      AiMessage.from("Nice to meet you, Matheus!")
    );

    observer.extractAndStore(messages);

    Embedding queryEmbedding = embeddingModel.embed("user name Matheus").content();
    EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
      EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(10)
        .minScore(0.5)
        .build()
    );

    boolean hasSource = results.matches().stream()
      .anyMatch(m -> "semantic-extractor".equals(m.embedded().metadata().getString("source")));
    assertTrue(hasSource, "Expected metadata source=semantic-extractor");
  }

  @Test
  void extractAndStore_handlesEmptyResponse() {
    when(chatModel.chat(anyString())).thenReturn("");

    List<ChatMessage> messages = List.of(new UserMessage("hello"));
    observer.extractAndStore(messages);
  }

  @Test
  void extractAndStore_handlesException() {
    when(chatModel.chat(anyString())).thenThrow(new RuntimeException("LLM unavailable"));

    List<ChatMessage> messages = List.of(new UserMessage("hello"));
    observer.extractAndStore(messages);
  }

  private void assertFactFound(EmbeddingSearchResult<TextSegment> results, String keyword) {
    boolean found = results.matches().stream()
      .map(EmbeddingMatch::embedded)
      .map(TextSegment::text)
      .anyMatch(text -> text.toLowerCase().contains(keyword.toLowerCase()));
    assertTrue(found, "Expected to find fact containing '" + keyword + "' but found: " +
      results.matches().stream().map(m -> m.embedded().text()).toList());
  }
}
