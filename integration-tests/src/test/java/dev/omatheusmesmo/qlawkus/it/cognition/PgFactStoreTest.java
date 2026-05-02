package dev.omatheusmesmo.qlawkus.it.cognition;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PgFactStoreTest {

  @Inject
  FactStore factStore;

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
  void searchRelevantFacts_returnsMatchingFacts() {
    Metadata metadata = new Metadata();
    metadata.put("source", "semantic-extractor");
    TextSegment segment = TextSegment.from("User prefers dark theme in IDE", metadata);
    Embedding embedding = embeddingModel.embed(segment).content();
    embeddingStore.add(embedding, segment);

    List<String> facts = factStore.search("IDE theme preference", 5, 0.75);

    assertFalse(facts.isEmpty());
    assertTrue(facts.stream().anyMatch(f -> f.toLowerCase().contains("dark theme")));
  }

  @Test
  void searchRelevantFacts_returnsMultipleFacts() {
    Metadata metadata = new Metadata();
    metadata.put("source", "semantic-extractor");

    TextSegment seg1 = TextSegment.from("User codes in Rust programming language", metadata);
    embeddingStore.add(embeddingModel.embed(seg1).content(), seg1);

    TextSegment seg2 = TextSegment.from("User works with Kubernetes and Docker", metadata);
    embeddingStore.add(embeddingModel.embed(seg2).content(), seg2);

    List<String> facts = factStore.search("programming and infrastructure tools", 10, 0.75);

    assertFalse(facts.isEmpty());
  }
}
