package dev.omatheusmesmo.qlawkus.cognition;

import dev.omatheusmesmo.qlawkus.repository.EmbeddingRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class EmbeddingService {

  @Inject
  EmbeddingModel embeddingModel;

  @Inject
  EmbeddingStore<TextSegment> embeddingStore;

  @Inject
  EmbeddingRepository embeddingRepository;

  public void store(String text, Metadata metadata) {
    try {
      String hash = EmbeddingRepository.md5(text);
      if (embeddingRepository.existsByContentHash(hash)) {
        Log.debugf("Embedding already exists for text, skipping: %s", text);
        return;
      }
      TextSegment segment = TextSegment.from(text, metadata);
      Embedding embedding = embeddingModel.embed(text).content();
      embeddingStore.add(embedding, segment);
    } catch (Exception e) {
      Log.warnf(e, "Failed to store embedding for text: %s", text);
    }
  }

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
}
