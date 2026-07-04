package dev.omatheusmesmo.qlawkus.store;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactChunkerTest {

  private static final Map<String, String> META = Map.of("source", "transcript", "memoryId", "m1");

  @Test
  void shortContentBecomesOneChunkCarryingTheWholeText() {
    FactChunker chunker = new FactChunker(1200, 120);
    String content = "User's name is Matheus.";

    List<FactChunker.Chunk> chunks = chunker.chunk(content, META);

    assertEquals(1, chunks.size(), "content under budget is a single segment");
    Map<String, String> m = chunks.get(0).metadata();
    assertEquals(FactChunker.factHash(content), m.get(FactChunker.FACT_HASH));
    assertEquals("0", m.get(FactChunker.CHUNK_INDEX));
    assertEquals("1", m.get(FactChunker.CHUNK_COUNT));
    assertEquals(content, m.get(FactChunker.FACT_TEXT), "segment 0 carries the whole fact text");
    assertEquals("transcript", m.get("source"), "base metadata is preserved");
  }

  @Test
  void longContentSplitsIntoManyChunksSharingOneFactHash() {
    FactChunker chunker = new FactChunker(400, 40);
    String content = "This sentence describes an unrelated topic in some detail. ".repeat(120);

    List<FactChunker.Chunk> chunks = chunker.chunk(content, META);

    assertTrue(chunks.size() > 1, "content over budget splits into several segments");
    String expectedHash = FactChunker.factHash(content);
    assertTrue(chunks.stream().allMatch(c -> expectedHash.equals(c.metadata().get(FactChunker.FACT_HASH))),
        "every segment shares the parent factHash");

    List<String> indices = chunks.stream()
        .map(c -> c.metadata().get(FactChunker.CHUNK_INDEX)).collect(Collectors.toList());
    for (int i = 0; i < chunks.size(); i++) {
      assertEquals(Integer.toString(i), indices.get(i), "chunk indices are sequential");
      assertEquals(Integer.toString(chunks.size()), chunks.get(i).metadata().get(FactChunker.CHUNK_COUNT));
    }

    assertEquals(content, chunks.get(0).metadata().get(FactChunker.FACT_TEXT),
        "segment 0 still holds the full fact for file reconstruction");
    assertTrue(chunks.stream().skip(1).noneMatch(c -> c.metadata().containsKey(FactChunker.FACT_TEXT)),
        "only segment 0 carries the whole text, avoiding N-fold duplication");

    chunks.forEach(c -> assertTrue(c.text().length() <= 400 + 40,
        "each segment stays within the character budget (+ overlap)"));
  }

  @Test
  void oversizedTranscriptStaysUnderTheEmbeddingBudget() {
    FactChunker chunker = new FactChunker(1200, 120);
    String oversized = "Long article summary paragraph with detail and nuance. ".repeat(220);

    List<FactChunker.Chunk> chunks = chunker.chunk(oversized, META);

    assertTrue(chunks.size() > 1, "an 832-token-class fact is broken up, never embedded whole");
    chunks.forEach(c -> assertTrue(c.text().length() <= 1200 + 120,
        "no segment exceeds the configured budget, so the embedding call never overflows"));
  }

  @Test
  void factHashIsStableAndContentAddressable() {
    String content = "A durable fact about the user.";
    assertEquals(FactChunker.factHash(content), FactChunker.factHash(content), "deterministic");
    assertFalse(FactChunker.factHash(content).equals(FactChunker.factHash(content + " ")),
        "distinct content yields a distinct hash");
  }
}
