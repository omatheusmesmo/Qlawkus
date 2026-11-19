package dev.omatheusmesmo.qlawkus.tool;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.langchain4j.service.tool.search.vector.VectorToolSearchStrategy;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.function.Supplier;

/**
 * Explicitly selected by AgentService. Deliberately not a ToolSearchStrategy bean:
 * Quarkus would otherwise auto-enable it on every unrelated AI service as well.
 */
@ApplicationScoped
public class QlawToolSearchStrategySupplier implements Supplier<ToolSearchStrategy> {

    @Inject
    AgentConfig config;

    @Inject
    EmbeddingModel embeddingModel;

    @Override
    public ToolSearchStrategy get() {
        return config.toolSearch().enabled() ? new VectorToolSearchStrategy(embeddingModel) : null;
    }
}
