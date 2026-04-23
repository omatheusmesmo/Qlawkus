package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class VectorFactStore {

  @Inject
  EmbeddingModel embeddingModel;

  @Inject
  EmbeddingStore<TextSegment> embeddingStore;

  public List<String> searchRelevantFacts(String query, int maxResults) {
    Embedding queryEmbedding = embeddingModel.embed(query).content();
    EmbeddingSearchResult<TextSegment> results = embeddingStore.search(
        EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(maxResults)
            .minScore(0.75)
            .build()
    );

    return results.matches().stream()
        .map(EmbeddingMatch::embedded)
        .map(TextSegment::text)
        .toList();
  }
}
