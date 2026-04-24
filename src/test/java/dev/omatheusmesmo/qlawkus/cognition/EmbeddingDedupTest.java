package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.document.Metadata;
import dev.omatheusmesmo.qlawkus.repository.EmbeddingRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EmbeddingDedupTest {

    @Inject
    EmbeddingService embeddingService;

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
        Metadata metadata = new Metadata();
        metadata.put("source", "dedup-test");

        embeddingService.store(text, metadata);
        embeddingService.store(text, metadata);

        long count = embeddingRepository.countBySource("dedup-test");
        assertTrue(count <= 1, "Duplicate text should result in at most one embedding, got " + count);
    }

    @Test
    void store_differentTexts_bothStored() {
        Metadata metadata = new Metadata();
        metadata.put("source", "dedup-test");

        embeddingService.store("User prefers dark theme", metadata);
        embeddingService.store("User prefers light theme", metadata);

        long count = embeddingRepository.countBySource("dedup-test");
        assertTrue(count >= 2, "Different texts should both be stored, got " + count);
    }

    @Test
    void existsByContentHash_returnsTrueAfterStore() {
        String text = "Dedup hash verification test";
        Metadata metadata = new Metadata();
        metadata.put("source", "dedup-test");

        embeddingService.store(text, metadata);

        String hash = EmbeddingRepository.md5(text);
        assertTrue(embeddingRepository.existsByContentHash(hash));
    }

    @Test
    void existsByContentHash_returnsFalseForUnknown() {
        String hash = EmbeddingRepository.md5("this text was never stored");
        assertFalse(embeddingRepository.existsByContentHash(hash));
    }
}
