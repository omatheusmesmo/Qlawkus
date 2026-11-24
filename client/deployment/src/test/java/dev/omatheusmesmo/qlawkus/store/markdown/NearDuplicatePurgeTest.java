package dev.omatheusmesmo.qlawkus.store.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The nightly purge removes a fact whose embedding sits within a cosine distance of another. That
 * distance cannot tell a reworded fact from a corrected one: "prefers dark theme" and "no longer
 * prefers dark theme" measured 0.9689 apart, 0.0011 under the default threshold, so the correction
 * was deleted as a duplicate. The purge therefore asks a judge about each candidate pair and removes
 * the fact only when the judge says it restates the other.
 */
class NearDuplicatePurgeTest {

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private static final String PREFERENCE = "User prefers dark theme in IDE";
  private static final String CORRECTION = "User no longer prefers dark theme in IDE";
  private static final double MAX_DISTANCE = 0.03;

  @TempDir
  Path tempDir;

  private MarkdownFactStore storeWithClosePair() {
    MarkdownFactStore store = new MarkdownFactStore(tempDir.toString(), new ScriptedEmbeddingModel(), JSON);
    store.load();
    store.store(PREFERENCE, Map.of("source", MemorySource.SEMANTIC_EXTRACTOR.value()));
    store.store(CORRECTION, Map.of("source", MemorySource.SEMANTIC_EXTRACTOR.value()));
    return store;
  }

  @Test
  void aCorrectionTheJudgeSaysIsNotARewordingSurvivesThePurge() {
    MarkdownFactStore store = storeWithClosePair();

    long removed = store.purgeNearDuplicates(MAX_DISTANCE, (kept, candidate) -> false);

    assertEquals(0, removed);
    assertEquals(Set.of(PREFERENCE, CORRECTION), Set.copyOf(store.listFactTexts(10)));
  }

  @Test
  void aRewordingTheJudgeConfirmsIsRemovedAndOneOfThePairStays() {
    MarkdownFactStore store = storeWithClosePair();

    long removed = store.purgeNearDuplicates(MAX_DISTANCE, (kept, candidate) -> true);

    assertEquals(1, removed);
    assertEquals(1, store.listFactTexts(10).size());
  }

  @Test
  void theJudgeIsAskedAboutTheKeptFactAndThenTheCandidateForRemoval() {
    MarkdownFactStore store = storeWithClosePair();
    List<List<String>> asked = new ArrayList<>();

    store.purgeNearDuplicates(MAX_DISTANCE, (kept, candidate) -> {
      asked.add(List.of(kept, candidate));
      return false;
    });

    assertEquals(1, asked.size(), "one close pair, one question");
    assertEquals(Set.of(PREFERENCE, CORRECTION), Set.copyOf(asked.get(0)));
  }

  @Test
  void aDistantPairIsNeverPutToTheJudge() {
    MarkdownFactStore store = new MarkdownFactStore(tempDir.toString(), new ScriptedEmbeddingModel(), JSON);
    store.load();
    store.store(PREFERENCE, Map.of("source", MemorySource.SEMANTIC_EXTRACTOR.value()));
    store.store("The owner keeps bees on the roof", Map.of("source", MemorySource.REMEMBER_TOOL.value()));
    List<String> asked = new ArrayList<>();

    store.purgeNearDuplicates(MAX_DISTANCE, (kept, candidate) -> {
      asked.add(candidate);
      return true;
    });

    assertTrue(asked.isEmpty());
    assertEquals(2, store.listFactTexts(10).size());
  }

  @Test
  void withoutAJudgeTheDistanceAloneStillDecides() {
    MarkdownFactStore store = storeWithClosePair();

    assertEquals(1, store.purgeNearDuplicates(MAX_DISTANCE));
    assertFalse(store.listFactTexts(10).isEmpty());
  }

  /** Puts the two preference facts at a chosen small angle and everything else far away. */
  private static final class ScriptedEmbeddingModel implements EmbeddingModel {

    private static final float[] BASE = {1f, 0f, 0f};
    private static final float[] CLOSE = {0.9996f, 0.0283f, 0f};
    private static final float[] FAR = {0f, 0f, 1f};

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
      List<Embedding> vectors = new ArrayList<>();
      for (TextSegment segment : segments) {
        String text = segment.text();
        vectors.add(Embedding.from(text.equals(PREFERENCE) ? BASE : text.equals(CORRECTION) ? CLOSE : FAR));
      }
      return Response.from(vectors);
    }

    @Override
    public int dimension() {
      return 3;
    }
  }
}
