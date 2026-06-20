package dev.omatheusmesmo.qlawkus.store.pg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
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
 * Unit test for the hybrid fact backend: every write must land in the {@code .md} files (the source
 * of truth) AND be mirrored into the vector store. Uses a real {@link InMemoryEmbeddingStore} as the
 * mirror, a deterministic fake embedding model, and a fake {@link EmbeddingRepository} (no
 * database), so the file/mirror logic is exercised without Postgres.
 */
class HybridFactStoreTest {

  @TempDir
  Path tempDir;

  private static Map<String, Object> source(MemorySource source) {
    return Map.of("source", source.value());
  }

  private HybridFactStore store(FakeRepo repo) {
    return new HybridFactStore(tempDir.toString(), new FakeEmbeddingModel(),
        new InMemoryEmbeddingStore<>(), repo);
  }

  private long markdownFileCount() throws IOException {
    try (Stream<Path> files = Files.list(tempDir)) {
      return files.filter(p -> p.getFileName().toString().endsWith(".md")).count();
    }
  }

  @Test
  void storeWritesFileAndMirrorsToVectorStore() throws IOException {
    HybridFactStore store = store(new FakeRepo());

    store.store("user prefers dark theme", source(MemorySource.REMEMBER_TOOL));

    assertEquals(1, markdownFileCount());
    assertEquals(List.of("user prefers dark theme"),
        store.search("user prefers dark theme", 1, 0.0));
  }

  @Test
  void storeDeduplicatesViaRepository() throws IOException {
    FakeRepo repo = new FakeRepo();
    repo.exists = true;
    HybridFactStore store = store(repo);

    store.store("already known", source(MemorySource.REMEMBER_TOOL));

    assertEquals(0, markdownFileCount());
  }

  @Test
  void purgeAllDeletesFilesAndDelegatesToRepository() throws IOException {
    FakeRepo repo = new FakeRepo();
    HybridFactStore store = store(repo);
    store.store("one", source(MemorySource.REMEMBER_TOOL));
    store.store("two", source(MemorySource.REMEMBER_TOOL));

    long purged = store.purgeAll();

    assertEquals(2, purged);
    assertEquals(1, repo.deleteAllCalls);
    assertEquals(0, markdownFileCount());
  }

  @Test
  void purgeBySourceDeletesMatchingFilesAndDelegates() throws IOException {
    FakeRepo repo = new FakeRepo();
    HybridFactStore store = store(repo);
    store.store("curated", source(MemorySource.REMEMBER_TOOL));
    store.store("transcript line", source(MemorySource.TRANSCRIPT));

    store.purgeBySource(MemorySource.TRANSCRIPT.value());

    assertEquals(MemorySource.TRANSCRIPT.value(), repo.deletedSource);
    assertEquals(1, markdownFileCount());
  }

  /** Fake repository: in-memory flags, no EntityManager, overrides only what the store calls. */
  private static final class FakeRepo extends EmbeddingRepository {

    boolean exists = false;
    int deleteAllCalls = 0;
    String deletedSource = null;

    @Override
    public boolean existsByContentHash(String hash) {
      return exists;
    }

    @Override
    public long deleteAll() {
      deleteAllCalls++;
      return 2;
    }

    @Override
    public long deleteBySource(String source) {
      deletedSource = source;
      return 1;
    }

    @Override
    public List<String> listSources() {
      return List.of();
    }
  }

  /** Deterministic, offline embedding model: same text always yields the same vector. */
  private static final class FakeEmbeddingModel implements EmbeddingModel {

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
      List<Embedding> embeddings = new ArrayList<>();
      for (TextSegment segment : segments) {
        Random random = new Random(segment.text().hashCode());
        float[] vector = new float[32];
        for (int i = 0; i < vector.length; i++) {
          vector[i] = random.nextFloat() * 2 - 1;
        }
        embeddings.add(Embedding.from(vector));
      }
      return Response.from(embeddings);
    }

    @Override
    public int dimension() {
      return 32;
    }
  }
}
