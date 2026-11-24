package dev.omatheusmesmo.qlawkus.cognition;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.omatheusmesmo.qlawkus.cognition.review.DuplicateJudge;
import dev.omatheusmesmo.qlawkus.cognition.review.DuplicateJudges;
import dev.omatheusmesmo.qlawkus.config.MemoryReviewConfig;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownFactStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The nightly job hands each close pair to the judge, so a correction survives a judge's "no". */
class MemoryReviewJobTest {

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private static final String PREFERENCE = "User prefers dark theme in IDE";
  private static final String CORRECTION = "User no longer prefers dark theme in IDE";

  @TempDir
  Path tempDir;

  private MemoryReviewJob jobOver(MarkdownFactStore store, DuplicateJudge judge) {
    MemoryReviewJob job = new MemoryReviewJob();
    job.meters = AgentMeters.disabled();
    job.factStore = store;
    job.judges = new DuplicateJudges(judge, AgentMeters.disabled());
    job.config = new MemoryReviewConfig() {
      @Override
      public double similarityThreshold() {
        return 0.97;
      }

      @Override
      public String cron() {
        return "0 30 3 * * ?";
      }
    };
    return job;
  }

  private MarkdownFactStore storeWithPair() {
    MarkdownFactStore store = new MarkdownFactStore(tempDir.toString(), new PairModel(), JSON);
    store.load();
    store.store(PREFERENCE, Map.of("source", MemorySource.SEMANTIC_EXTRACTOR.value()));
    store.store(CORRECTION, Map.of("source", MemorySource.SEMANTIC_EXTRACTOR.value()));
    return store;
  }

  @Test
  void aCorrectionTheJudgeRejectsIsNotPurgedAsADuplicate() {
    MarkdownFactStore store = storeWithPair();

    long removed = jobOver(store, (kept, candidate) -> false).reviewNow();

    assertEquals(0, removed);
    assertEquals(2, store.listFactTexts(10).size());
  }

  @Test
  void aRewordingTheJudgeConfirmsIsPurged() {
    MarkdownFactStore store = storeWithPair();

    long removed = jobOver(store, (kept, candidate) -> true).reviewNow();

    assertEquals(1, removed);
  }

  /** The two facts have 0.971 cosine similarity (0.029 distance): a correction a hair over the 0.97 similarity threshold. */
  private static final class PairModel implements EmbeddingModel {

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
      List<Embedding> vectors = new ArrayList<>();
      for (TextSegment segment : segments) {
        vectors.add(Embedding.from(segment.text().equals(PREFERENCE) ? new float[] {1f, 0f}
            : new float[] {0.9710f, 0.2390f}));
      }
      return Response.from(vectors);
    }

    @Override
    public int dimension() {
      return 2;
    }
  }
}
