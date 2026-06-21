package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import dev.omatheusmesmo.qlawkus.store.markdown.MarkdownFactStore;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import jakarta.enterprise.context.ApplicationScoped;

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

  /**
   * Frames retrieved facts in the user message and surfaces each fact's {@code source} as
   * provenance, so the model knows these are recalled memories (not the user's words) and where
   * they came from. Empty retrieval is a no-op, so this is safe to apply unconditionally.
   *
   * <p>The injector's prompt template is not native-image safe to build at image build time, so
   * {@code ClientProcessor} marks this class for runtime class initialization
   * ({@code RuntimeInitializedClassBuildItem}); the field is then initialized at runtime.
   */
  private static final ContentInjector MEMORY_INJECTOR = DefaultContentInjector.builder()
      .promptTemplate(PromptTemplate.from("{{userMessage}}\n\n"
          + "Relevant things you remember (each item is a stored memory, with its source):\n"
          + "{{contents}}"))
      .metadataKeysToInclude(List.of("source"))
      .build();

  @Override
  public RetrievalAugmentor get() {
    AgentConfig.ActiveMemory activeMemory =
        Arc.container().instance(AgentConfig.class).get().activeMemory();
    boolean enabled = activeMemory.enabled();
    int maxResults = activeMemory.maxResults();
    double minScore = activeMemory.minScore();

    ContentRetriever retriever;
    if (enabled) {
      EmbeddingStore<TextSegment> store = resolveEmbeddingStore();
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
    return DefaultRetrievalAugmentor.builder()
        .contentRetriever(retriever)
        .contentInjector(MEMORY_INJECTOR)
        .build();
  }

  /**
   * The embedding store to retrieve facts from, chosen by the active cognition backend. In markdown
   * mode the {@link MarkdownFactStore} bean is present and owns an in-process index (no database);
   * otherwise the Postgres-backed {@code EmbeddingStore} (pgvector / hybrid) is used. Both flow
   * through the same {@code EmbeddingStoreContentRetriever}.
   */
  @SuppressWarnings("unchecked")
  private EmbeddingStore<TextSegment> resolveEmbeddingStore() {
    InstanceHandle<MarkdownFactStore> markdown = Arc.container().instance(MarkdownFactStore.class);
    if (markdown.isAvailable()) {
      return markdown.get().embeddingStore();
    }
    return Arc.container().instance(EmbeddingStore.class).get();
  }
}
