package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.List;
import java.util.function.Supplier;

/**
 * Active memory: a retrieval step that runs before every reply and injects query-relevant memories
 * into the prompt, so recall no longer depends on the model choosing to call searchMemories.
 * Mirrors OpenClaw's "active memory" — most memory systems are reactive; this gives the agent one
 * bounded chance to surface relevant memory before the reply is generated.
 *
 * <p>Wired as the {@code retrievalAugmentor} of the agent AI service. Config-gated via
 * {@code qlawkus.agent.active-memory.*}; when disabled it injects nothing.
 */
@ApplicationScoped
public class ActiveMemoryAugmentor implements Supplier<RetrievalAugmentor> {

  @Override
  public RetrievalAugmentor get() {
    var config = ConfigProvider.getConfig();
    boolean enabled = config.getOptionalValue("qlawkus.agent.active-memory.enabled", Boolean.class)
        .orElse(true);
    int maxResults = config.getOptionalValue("qlawkus.agent.active-memory.max-results", Integer.class)
        .orElse(5);
    double minScore = config.getOptionalValue("qlawkus.agent.active-memory.min-score", Double.class)
        .orElse(0.7);

    ContentRetriever retriever;
    if (enabled) {
      @SuppressWarnings("unchecked")
      EmbeddingStore<TextSegment> store = Arc.container().instance(EmbeddingStore.class).get();
      EmbeddingModel model = Arc.container().instance(EmbeddingModel.class).get();
      retriever = EmbeddingStoreContentRetriever.builder()
          .embeddingStore(store)
          .embeddingModel(model)
          .maxResults(maxResults)
          .minScore(minScore)
          .filter(metadataKey("source").isNotEqualTo(MemorySource.TRANSCRIPT.value()))
          .build();
    } else {
      retriever = query -> List.of();
    }
    return DefaultRetrievalAugmentor.builder().contentRetriever(retriever).build();
  }
}
