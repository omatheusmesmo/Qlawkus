package dev.omatheusmesmo.qlawkus.store.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.omatheusmesmo.qlawkus.store.FactChunker;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
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

  /** Deterministic, offline embedding model: same text always yields the same vector. */
  private static final class FakeEmbeddingModel implements EmbeddingModel {

    int embedCalls = 0;

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
      embedCalls += segments.size();
      List<Embedding> embeddings = new ArrayList<>();
      for (TextSegment segment : segments) {
        embeddings.add(vectorFor(segment.text()));
      }
      return Response.from(embeddings);
    }

    @Override
    public int dimension() {
      return 32;
    }

    private static Embedding vectorFor(String text) {
      Random random = new Random(text.hashCode());
      float[] vector = new float[32];
      for (int i = 0; i < vector.length; i++) {
        vector[i] = random.nextFloat() * 2 - 1;
      }
      return Embedding.from(vector);
    }
  }
}
