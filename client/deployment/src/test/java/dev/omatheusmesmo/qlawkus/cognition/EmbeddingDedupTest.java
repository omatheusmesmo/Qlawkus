package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.document.Metadata;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
@QuarkusTest
class EmbeddingDedupTest {

  @Inject
  FactStore factStore;

  @Inject
  EmbeddingRepository embeddingRepository;

  @AfterEach
  @Transactional
  void cleanup() {
    embeddingRepository.deleteAll();
  }

  @Test
  void store_sameTextTwice_onlyOneEmbedding() {
    String text = "User prefers dark theme in IDE";
    Map<String, Object> metadata = Map.of("source", "dedup-test");

    factStore.store(text, metadata);
    factStore.store(text, metadata);

    long count = embeddingRepository.countBySource("dedup-test");
    assertTrue(count <= 1, "Duplicate text should result in at most one embedding, got " + count);
  }

  @Test
  void store_differentTexts_bothStored() {
    Map<String, Object> metadata = Map.of("source", "dedup-test");

    factStore.store("User prefers dark theme", metadata);
    factStore.store("User prefers light theme", metadata);

    long count = embeddingRepository.countBySource("dedup-test");
    assertTrue(count >= 2, "Different texts should both be stored, got " + count);
  }

  @Test
  void existsByContentHash_returnsTrueAfterStore() {
    String text = "Dedup hash verification test";
    Map<String, Object> metadata = Map.of("source", "dedup-test");

    factStore.store(text, metadata);

    String hash = EmbeddingRepository.md5(text);
    assertTrue(embeddingRepository.existsByContentHash(hash));
  }

  @Test
  void existsByContentHash_returnsFalseForUnknown() {
    String hash = EmbeddingRepository.md5("this text was never stored");
    assertFalse(embeddingRepository.existsByContentHash(hash));
  }
}
