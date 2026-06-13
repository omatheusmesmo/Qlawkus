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
import dev.omatheusmesmo.qlawkus.store.FactStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PgFactStore implements FactStore {

  @Inject
  EmbeddingModel embeddingModel;

  @Inject
  EmbeddingStore<TextSegment> embeddingStore;

  @Inject
  EmbeddingRepository embeddingRepository;

  @Override
  public void store(String content, Map<String, Object> metadata) {
    String hash = EmbeddingRepository.md5(content);
    if (embeddingRepository.existsByContentHash(hash)) {
      Log.debugf("Embedding already exists for text, skipping: %s", content);
      return;
    }
    Metadata langchainMetadata = new Metadata();
    metadata.forEach((key, value) -> {
      switch (value) {
        case String s -> langchainMetadata.put(key, s);
        case Integer i -> langchainMetadata.put(key, i);
        case Long l -> langchainMetadata.put(key, l);
        case Double d -> langchainMetadata.put(key, d);
        case Float f -> langchainMetadata.put(key, f);
        default -> langchainMetadata.put(key, value.toString());
      }
    });
    TextSegment segment = TextSegment.from(content, langchainMetadata);
    Embedding embedding = embeddingModel.embed(content).content();
    embeddingStore.add(embedding, segment);
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
  public long purgeBySource(String source) {
    return embeddingRepository.deleteBySource(source);
  }

  @Override
  public long purgeAll() {
    return embeddingRepository.deleteAll();
  }
}
