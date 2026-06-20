package dev.omatheusmesmo.qlawkus.store.pg;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownFactFiles;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid {@link FactStore}, active when {@code qlawkus.cognition.backend=hybrid}. Facts are written
 * to {@code .md} files (the editable, git-versionable source of truth) and mirrored into pgvector;
 * retrieval runs against pgvector so it scales and persists. Selected at build time via
 * {@link IfBuildProperty}.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "hybrid")
public class HybridFactStore implements FactStore {

  private final MarkdownFactFiles files;
  private final EmbeddingModel embeddingModel;
  private final EmbeddingStore<TextSegment> embeddingStore;
  private final EmbeddingRepository embeddingRepository;

  @Inject
  public HybridFactStore(AgentConfig config, EmbeddingModel embeddingModel,
      EmbeddingStore<TextSegment> embeddingStore, EmbeddingRepository embeddingRepository) {
    this(config.facts().root(), embeddingModel, embeddingStore, embeddingRepository);
  }

  HybridFactStore(String root, EmbeddingModel embeddingModel,
      EmbeddingStore<TextSegment> embeddingStore, EmbeddingRepository embeddingRepository) {
    this.files = new MarkdownFactFiles(Path.of(root));
    this.embeddingModel = embeddingModel;
    this.embeddingStore = embeddingStore;
    this.embeddingRepository = embeddingRepository;
  }

  @Override
  public void store(String content, Map<String, Object> metadata) {
    String id = EmbeddingRepository.md5(content);
    if (embeddingRepository.existsByContentHash(id)) {
      Log.debugf("Fact already exists, skipping: %s", id);
      return;
    }
    files.write(id, content, stringify(metadata));
    Metadata segmentMetadata = new Metadata();
    metadata.forEach((key, value) -> segmentMetadata.put(key, value == null ? "" : value.toString()));
    Embedding embedding = embeddingModel.embed(content).content();
    embeddingStore.add(embedding, TextSegment.from(content, segmentMetadata));
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
    return embeddingRepository.listSources();
  }

  @Override
  public long purgeBySource(String source) {
    files.loadAll().stream()
        .filter(fact -> source.equals(fact.metadata().get("source")))
        .forEach(fact -> files.delete(fact.id()));
    return embeddingRepository.deleteBySource(source);
  }

  @Override
  public long purgeAll() {
    files.loadAll().forEach(fact -> files.delete(fact.id()));
    return embeddingRepository.deleteAll();
  }

  private List<String> texts(EmbeddingSearchRequest request) {
    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
    return result.matches().stream().map(EmbeddingMatch::embedded).map(TextSegment::text).toList();
  }

  private static Map<String, String> stringify(Map<String, Object> metadata) {
    Map<String, String> out = new LinkedHashMap<>();
    metadata.forEach((key, value) -> out.put(key, value == null ? "" : value.toString()));
    return out;
  }
}
