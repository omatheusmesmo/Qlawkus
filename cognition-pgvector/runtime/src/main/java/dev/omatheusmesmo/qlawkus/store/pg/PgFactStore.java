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
import dev.omatheusmesmo.qlawkus.store.FactChunker;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Postgres/pgvector-backed {@link FactStore}, active when {@code qlawkus.cognition.backend=pgvector}
 * (the default). Selected at build time via {@link IfBuildProperty}, so other backends do not wire
 * it at all.
 */
@ApplicationScoped
@IfBuildProperty(name = "qlawkus.cognition.backend", stringValue = "pgvector", enableIfMissing = true)
public class PgFactStore implements FactStore {

  @Inject
  EmbeddingModel embeddingModel;

  @Inject
  EmbeddingStore<TextSegment> embeddingStore;

  @Inject
  EmbeddingRepository embeddingRepository;

  @Inject
  AgentConfig agentConfig;

  private FactChunker chunker;

  @PostConstruct
  void init() {
    chunker = new FactChunker(agentConfig.facts().chunkMaxChars(),
        agentConfig.facts().chunkOverlapChars());
  }

  @Override
  public void store(String content, Map<String, Object> metadata) {
    String factHash = FactChunker.factHash(content);
    if (embeddingRepository.existsByContentHash(factHash)) {
      Log.debugf("Fact already exists, skipping: %s", factHash);
      return;
    }
    for (FactChunker.Chunk chunk : chunker.chunk(content, stringify(metadata))) {
      Metadata segmentMetadata = new Metadata();
      chunk.metadata().forEach(segmentMetadata::put);
      Embedding embedding = embeddingModel.embed(chunk.text()).content();
      embeddingStore.add(embedding, TextSegment.from(chunk.text(), segmentMetadata));
    }
  }

  private static Map<String, String> stringify(Map<String, Object> metadata) {
    Map<String, String> out = new LinkedHashMap<>();
    metadata.forEach((key, value) -> out.put(key, value == null ? "" : value.toString()));
    return out;
  }

  @Override
  public List<String> search(String query, int maxResults, double minScore) {
    Embedding queryEmbedding = embeddingModel.embed(query).content();
    EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
      EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(maxResults)
        .minScore(minScore)
        .build()
    );

        return results.matches().stream()
                .map(EmbeddingMatch::embedded)
                .map(TextSegment::text)
                .toList();
  }

  @Override
  public List<String> searchBySource(String query, String source, int maxResults, double minScore) {
    Embedding queryEmbedding = embeddingModel.embed(query).content();
    EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
        EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(maxResults)
            .minScore(minScore)
            .filter(metadataKey("source").isEqualTo(source))
            .build());

    return results.matches().stream()
        .map(EmbeddingMatch::embedded)
        .map(TextSegment::text)
        .toList();
  }

  @Override
  public List<String> listSources() {
    return embeddingRepository.listSources();
  }

  @Override
  public List<String> listFactTexts(int limit) {
    return embeddingRepository.listFactTexts(limit);
  }

  @Override
  public long purgeNearDuplicates(double maxCosineDistance) {
    return embeddingRepository.deleteNearDuplicates(maxCosineDistance);
  }

  @Override
  public long purgeBySource(String source) {
    return embeddingRepository.deleteBySource(source);
  }

  @Override
  public long purgeAll() {
    return embeddingRepository.deleteAll();
  }
}
