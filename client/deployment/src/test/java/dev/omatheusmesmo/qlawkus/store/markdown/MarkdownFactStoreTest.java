package dev.omatheusmesmo.qlawkus.store.markdown;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.store.FactChunker;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit test for the markdown fact backend: files are the source of truth, an in-process embedding
 * store gives query-relevant top-K retrieval, and a JSON cache avoids re-embedding on reload. Uses
 * a deterministic fake {@link EmbeddingModel} (same text yields the same vector) so an exact match
 * scores 1.0 - no real LLM needed.
 */
class MarkdownFactStoreTest {

  @TempDir
  Path tempDir;

  private static Map<String, Object> source(MemorySource source) {
    return Map.of("source", source.value());
  }

  private MarkdownFactStore freshStore(EmbeddingModel model) {
    MarkdownFactStore store = new MarkdownFactStore(tempDir.toString(), model);
    store.load();
    return store;
  }

  /**
   * A cache written by one embedding model holds vectors another cannot be compared with, even at the
   * same dimension, so a model change must re-embed rather than load them. This is the markdown half
   * of #357, where NVIDIA's retired 1024 model was replaced by a 2048 one.
   */
  @Test
  void aChangedEmbeddingModelReembedsRatherThanLoadingVectorsItCannotCompareWith() {
    MarkdownFactStore before = new MarkdownFactStore(tempDir.toString(), new FakeEmbeddingModel(), "model-a");
    before.load();
    before.store("the owner keeps bees on the roof", source(MemorySource.REMEMBER_TOOL));

    FakeEmbeddingModel successor = new FakeEmbeddingModel(48);
    MarkdownFactStore after = new MarkdownFactStore(tempDir.toString(), successor, "model-b");
    after.load();

    assertEquals(1, successor.embedCalls, "the cached vector belongs to another model");
    assertFalse(after.search("bees on the roof", 5, 0.0).isEmpty(), "and the fact is still found");
  }

  /**
   * A model change re-embeds every fact at load, which makes one provider error far more likely to
   * land there. It must cost that fact, not the store: a load that throws leaves every caller of the
   * fact store failing, while a skipped fact is simply retried at the next boot, since it was never
   * cached.
   */
  @Test
  void aFactThatFailsToEmbedAtLoadIsSkippedRatherThanFailingTheStore() {
    MarkdownFactStore before = new MarkdownFactStore(tempDir.toString(), new FakeEmbeddingModel(), "model-a");
    before.load();
    before.store("the owner keeps bees on the roof", source(MemorySource.REMEMBER_TOOL));
    before.store("a poison fact the provider refuses", source(MemorySource.REMEMBER_TOOL));

    MarkdownFactStore after = new MarkdownFactStore(tempDir.toString(), new FakeEmbeddingModel(48, "poison"),
        "model-b");

    assertDoesNotThrow(after::load, "one fact the provider refuses must not take down the store");
    assertFalse(after.search("bees on the roof", 5, 0.0).isEmpty(), "the other facts still load");
  }

  @Test
  void anUnchangedEmbeddingModelReusesItsCache() {
    MarkdownFactStore before = new MarkdownFactStore(tempDir.toString(), new FakeEmbeddingModel(), "model-a");
    before.load();
    before.store("the owner keeps bees on the roof", source(MemorySource.REMEMBER_TOOL));

    FakeEmbeddingModel same = new FakeEmbeddingModel();
    new MarkdownFactStore(tempDir.toString(), same, "model-a").load();

    assertEquals(0, same.embedCalls, "a restart with the same model must not call the provider again");
  }

  private long markdownFileCount() throws IOException {
    try (Stream<Path> files = Files.list(tempDir)) {
      return files.filter(p -> p.getFileName().toString().endsWith(".md")).count();
    }
  }

  @Test
  void storesFactAsFileAndRetrievesItByRelevance() {
    MarkdownFactStore store = freshStore(new FakeEmbeddingModel());
    store.store("the sky is blue", source(MemorySource.REMEMBER_TOOL));
    store.store("cats enjoy fish", source(MemorySource.REMEMBER_TOOL));

    List<String> hits = store.search("the sky is blue", 1, 0.0);

    assertEquals(1, hits.size());
    assertEquals("the sky is blue", hits.get(0));
  }

  @Test
  void deduplicatesIdenticalContent() throws IOException {
    MarkdownFactStore store = freshStore(new FakeEmbeddingModel());
    store.store("the sky is blue", source(MemorySource.REMEMBER_TOOL));
    store.store("the sky is blue", source(MemorySource.REMEMBER_TOOL));

    assertEquals(1, markdownFileCount());
  }

  @Test
  void searchBySourceFiltersOutOtherSources() {
    MarkdownFactStore store = freshStore(new FakeEmbeddingModel());
    store.store("curated fact", source(MemorySource.REMEMBER_TOOL));
    store.store("raw transcript line", source(MemorySource.TRANSCRIPT));

    List<String> transcripts = store.searchBySource("curated fact", MemorySource.TRANSCRIPT.value(), 10, 0.0);

    assertFalse(transcripts.contains("curated fact"));
    assertTrue(transcripts.contains("raw transcript line"));
  }

  @Test
  void purgeBySourceRemovesOnlyThatSource() {
    MarkdownFactStore store = freshStore(new FakeEmbeddingModel());
    store.store("curated fact", source(MemorySource.REMEMBER_TOOL));
    store.store("raw transcript line", source(MemorySource.TRANSCRIPT));

    long purged = store.purgeBySource(MemorySource.TRANSCRIPT.value());

    assertEquals(1, purged);
    assertEquals(List.of(MemorySource.REMEMBER_TOOL.value()), store.listSources());
    assertEquals(List.of("curated fact"), store.search("curated fact", 1, 0.0));
  }

  @Test
  void purgeAllClearsFactsAndFiles() throws IOException {
    MarkdownFactStore store = freshStore(new FakeEmbeddingModel());
    store.store("one", source(MemorySource.REMEMBER_TOOL));
    store.store("two", source(MemorySource.REMEMBER_TOOL));

    long purged = store.purgeAll();

    assertEquals(2, purged);
    assertEquals(0, markdownFileCount());
    assertTrue(store.search("one", 5, 0.0).isEmpty());
  }

  @Test
  void reloadUsesEmbeddingCacheAndDoesNotReembed() {
    FakeEmbeddingModel writer = new FakeEmbeddingModel();
    MarkdownFactStore first = freshStore(writer);
    first.store("the sky is blue", source(MemorySource.REMEMBER_TOOL));

    FakeEmbeddingModel reader = new FakeEmbeddingModel();
    MarkdownFactStore second = freshStore(reader);

    assertEquals(0, reader.embedCalls, "reload must hit the cache, not re-embed stored facts");
    assertEquals(List.of("the sky is blue"), second.search("the sky is blue", 1, 0.0));
  }

  @Test
  void oversizedFactBecomesOneFileButManySegmentVectors() throws IOException {
    FakeEmbeddingModel writer = new FakeEmbeddingModel();
    MarkdownFactStore store = new MarkdownFactStore(tempDir.toString(), writer, new FactChunker(100, 10));
    store.load();
    String big = "Alpha beta gamma delta epsilon zeta eta theta iota. ".repeat(15);

    store.store(big, source(MemorySource.TRANSCRIPT));

    assertEquals(1, markdownFileCount(), "an oversized fact is still a single .md file");
    assertTrue(writer.embedCalls > 1, "content over the budget is embedded as several segments");
    assertFalse(store.search("alpha beta gamma", 3, 0.0).isEmpty(), "the chunked fact is retrievable");

    FakeEmbeddingModel reader = new FakeEmbeddingModel();
    MarkdownFactStore reloaded = new MarkdownFactStore(tempDir.toString(), reader, new FactChunker(100, 10));
    reloaded.load();
    assertEquals(0, reader.embedCalls, "reload hits the per-segment cache, not the model");
  }

  /**
   * The wiring regression. Metering the fan-out inside {@link FactChunker} reported success before
   * anything had been embedded, so a store whose model rejects every call still looked healthy. The
   * meter belongs around the embed, and only a store-level test can prove it is actually attached
   * there rather than merely available.
   */
  @Test
  void aStoreWhoseEmbeddingModelFailsReportsAFailedFact() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AgentMeters meters = new AgentMeters(registry);
    MarkdownFactStore store = new MarkdownFactStore(
        tempDir.toString(), new FailingEmbeddingModel(), new FactChunker(1200, 120), meters);

    assertThrows(RuntimeException.class,
        () -> store.store("the sky is blue", source(MemorySource.REMEMBER_TOOL)));

    assertEquals(1.0, registry.get(AgentMeters.EMBEDDING_FACTS).tag("outcome", "failure")
        .counter().count(), "the failed embed must surface as a failed fact");
  }

  /** A successful store still reports success, so the counter is not merely a failure detector. */
  @Test
  void aStoreThatEmbedsCleanlyReportsASuccessfulFact() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AgentMeters meters = new AgentMeters(registry);
    MarkdownFactStore store = new MarkdownFactStore(
        tempDir.toString(), new FakeEmbeddingModel(), new FactChunker(1200, 120), meters);
    store.load();

    store.store("the sky is blue", source(MemorySource.REMEMBER_TOOL));

    assertEquals(1.0, registry.get(AgentMeters.EMBEDDING_FACTS).tag("outcome", "success")
        .counter().count());
  }

  /** An embedding model that always rejects, standing in for a provider outage. */
  private static final class FailingEmbeddingModel implements EmbeddingModel {

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
      throw new IllegalStateException("embedding provider is down");
    }

    @Override
    public int dimension() {
      return 32;
    }
  }

  /** Deterministic, offline embedding model: same text always yields the same vector. */
  private static final class FakeEmbeddingModel implements EmbeddingModel {

    int embedCalls = 0;
    private final int dimension;
    private final String refused;

    FakeEmbeddingModel() {
      this(32);
    }

    FakeEmbeddingModel(int dimension) {
      this(dimension, null);
    }

    FakeEmbeddingModel(int dimension, String refused) {
      this.dimension = dimension;
      this.refused = refused;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
      embedCalls += segments.size();
      List<Embedding> embeddings = new ArrayList<>();
      for (TextSegment segment : segments) {
        if (refused != null && segment.text().contains(refused)) {
          throw new IllegalStateException("the provider refused this input");
        }
        embeddings.add(vectorFor(segment.text(), dimension));
      }
      return Response.from(embeddings);
    }

    @Override
    public int dimension() {
      return dimension;
    }

    private static Embedding vectorFor(String text, int dimension) {
      Random random = new Random(text.hashCode());
      float[] vector = new float[dimension];
      for (int i = 0; i < vector.length; i++) {
        vector[i] = random.nextFloat() * 2 - 1;
      }
      return Embedding.from(vector);
    }
  }
}
