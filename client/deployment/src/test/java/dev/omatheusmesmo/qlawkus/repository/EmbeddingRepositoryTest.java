package dev.omatheusmesmo.qlawkus.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EmbeddingRepositoryTest {

    @Test
    void md5_returnsConsistentHash() {
        String text = "User prefers dark theme in IDE";

        String hash1 = EmbeddingRepository.md5(text);
        String hash2 = EmbeddingRepository.md5(text);

        assertEquals(hash1, hash2);
        assertEquals(32, hash1.length());
    }

    @Test
    void md5_returnsDifferentHashForDifferentText() {
        String hash1 = EmbeddingRepository.md5("User prefers dark theme");
        String hash2 = EmbeddingRepository.md5("User prefers light theme");

        assertNotEquals(hash1, hash2);
    }
}
