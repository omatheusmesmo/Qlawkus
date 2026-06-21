package dev.omatheusmesmo.qlawkus.store.markdown;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Markdown-backed {@link FactStore}, active when {@code qlawkus.cognition.backend=markdown}. Facts
 * are {@code .md} files (the source of truth) under {@code qlawkus.agent.facts.root}; an in-process
 * {@link InMemoryEmbeddingStore} holds their embeddings for query-relevant retrieval (top-K), with
 * a sibling JSON cache so restarts only re-embed changed facts. No database is used. The same
 * embedding store is read by {@code ActiveMemoryAugmentor}, so recall stays on the RAG seam exactly
 * like the pgvector backend - retrieved, not dumped.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "markdown")
public class MarkdownFactStore implements FactStore {

  private final MarkdownFactFiles files;
  private final EmbeddingModel embeddingModel;
  private final InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

  @Inject
  public MarkdownFactStore(AgentConfig config, EmbeddingModel embeddingModel) {
    this(config.facts().root(), embeddingModel);
  }

  public MarkdownFactStore(String root, EmbeddingModel embeddingModel) {
    this.files = new MarkdownFactFiles(Path.of(root));
    this.embeddingModel = embeddingModel;
  }

  /**
   * Rebuilds the in-process embedding index from the {@code .md} files, re-embedding only facts
   * absent from the JSON cache. Runs automatically as the CDI {@code @PostConstruct}; also callable
   * directly when the store is constructed outside the container (tests).
   */
  @PostConstruct
  public void load() {
    Map<String, float[]> cache = files.loadCache();
    Map<String, float[]> refreshed = new LinkedHashMap<>();
    boolean changed = false;
    for (MarkdownFactFiles.FactRecord fact : files.loadAll()) {
      float[] vector = cache.get(fact.id());
      Embedding embedding;
      if (vector != null) {
        embedding = Embedding.from(vector);
      } else {
        embedding = embeddingModel.embed(fact.content()).content();
        changed = true;
      }
      refreshed.put(fact.id(), embedding.vector());
      store.add(fact.id(), embedding, segment(fact.content(), fact.metadata()));
    }
    if (changed || refreshed.size() != cache.size()) {
      files.saveCache(refreshed);
    }
    Log.infof("Markdown fact store loaded %d facts", refreshed.size());
  }

  @Override
  public void store(String content, Map<String, Object> metadata) {
    String id = MarkdownFactFiles.md5(content);
    if (files.exists(id)) {
      Log.debugf("Fact already exists, skipping: %s", id);
      return;
    }
    Map<String, String> stringMetadata = stringify(metadata);
    files.write(id, content, stringMetadata);
    Embedding embedding = embeddingModel.embed(content).content();
    store.add(id, embedding, segment(content, stringMetadata));
    Map<String, float[]> cache = files.loadCache();
    cache.put(id, embedding.vector());
    files.saveCache(cache);
  }

  @Override
  public List<String> search(String query, int maxResults, double minScore) {
    return texts(EmbeddingSearchRequest.builder()
        .queryEmbedding(embeddingModel.embed(query).content())
        .maxResults(maxResults)
        .minScore(minScore)
        .build());
  }

  @Override
  public List<String> searchBySource(String query, String source, int maxResults, double minScore) {
    return texts(EmbeddingSearchRequest.builder()
        .queryEmbedding(embeddingModel.embed(query).content())
        .maxResults(maxResults)
        .minScore(minScore)
        .filter(metadataKey("source").isEqualTo(source))
        .build());
  }

  @Override
  public List<String> listSources() {
    return files.listSources();
  }

  @Override
  public List<String> listFactTexts(int limit) {
    List<String> texts = new ArrayList<>();
    for (MarkdownFactFiles.FactRecord fact : files.loadAll()) {
      String source = fact.metadata().get("source");
      if (MemorySource.REMEMBER_TOOL.value().equals(source)
          || MemorySource.SEMANTIC_EXTRACTOR.value().equals(source)) {
        texts.add(fact.content());
        if (texts.size() >= limit) {
          break;
        }
      }
    }
    return texts;
  }

  @Override
  public long purgeNearDuplicates(double maxCosineDistance) {
    List<MarkdownFactFiles.FactRecord> facts = files.loadAll();
    Map<String, float[]> cache = files.loadCache();
    List<String> toRemove = new ArrayList<>();
    for (int i = 0; i < facts.size(); i++) {
      String idA = facts.get(i).id();
      if (toRemove.contains(idA)) {
        continue;
      }
      float[] a = cache.get(idA);
      if (a == null) {
        continue;
      }
      for (int j = i + 1; j < facts.size(); j++) {
        String idB = facts.get(j).id();
        if (toRemove.contains(idB)) {
          continue;
        }
        float[] b = cache.get(idB);
        if (b != null && cosineDistance(a, b) < maxCosineDistance) {
          toRemove.add(idB);
        }
      }
    }
    if (!toRemove.isEmpty()) {
      toRemove.forEach(files::delete);
      store.removeAll(toRemove);
      Map<String, float[]> refreshed = files.loadCache();
      toRemove.forEach(refreshed::remove);
      files.saveCache(refreshed);
    }
    return toRemove.size();
  }

  private static double cosineDistance(float[] a, float[] b) {
    double dot = 0;
    double normA = 0;
    double normB = 0;
    int len = Math.min(a.length, b.length);
    for (int i = 0; i < len; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }
    if (normA == 0 || normB == 0) {
      return 1.0;
    }
    return 1.0 - (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
  }

  @Override
  public long purgeBySource(String source) {
    List<String> ids = new ArrayList<>();
    for (MarkdownFactFiles.FactRecord fact : files.loadAll()) {
      if (source.equals(fact.metadata().get("source"))) {
        ids.add(fact.id());
      }
    }
    ids.forEach(files::delete);
    if (!ids.isEmpty()) {
      store.removeAll(ids);
      Map<String, float[]> cache = files.loadCache();
      ids.forEach(cache::remove);
      files.saveCache(cache);
    }
    return ids.size();
  }

  @Override
  public long purgeAll() {
    List<MarkdownFactFiles.FactRecord> all = files.loadAll();
    all.forEach(fact -> files.delete(fact.id()));
    store.removeAll();
    files.saveCache(new LinkedHashMap<>());
    return all.size();
  }

  /** The in-process embedding store backing this fact store; read by the active-memory augmentor. */
  public EmbeddingStore<TextSegment> embeddingStore() {
    return store;
  }

  private List<String> texts(EmbeddingSearchRequest request) {
    EmbeddingSearchResult<TextSegment> result = store.search(request);
    return result.matches().stream().map(EmbeddingMatch::embedded).map(TextSegment::text).toList();
  }

  private static TextSegment segment(String content, Map<String, String> metadata) {
    Metadata segmentMetadata = new Metadata();
    metadata.forEach(segmentMetadata::put);
    return TextSegment.from(content, segmentMetadata);
  }

  private static Map<String, String> stringify(Map<String, Object> metadata) {
    Map<String, String> out = new LinkedHashMap<>();
    metadata.forEach((key, value) -> out.put(key, value == null ? "" : value.toString()));
    return out;
  }
}
