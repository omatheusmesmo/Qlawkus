package dev.omatheusmesmo.qlawkus.cognition;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class VectorFactStore {

  @Inject
  EmbeddingService embeddingService;

  public List<String> searchRelevantFacts(String query, int maxResults) {
    return embeddingService.search(query, maxResults, 0.75);
  }
}
