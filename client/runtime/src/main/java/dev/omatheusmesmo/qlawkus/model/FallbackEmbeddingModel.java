package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkus.logging.Log;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.util.List;
import java.util.function.Supplier;

/**
 * Wraps the primary embedding model with retries, a circuit breaker and a switch to the Ollama
 * fallback, all from {@link EmbeddingGuard}.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class FallbackEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final EmbeddingModel fallback;
    private final EmbeddingGuard guard;
    private final ModelFallbackConfig config;

    @Inject
    public FallbackEmbeddingModel(
            @PrimaryEmbedding EmbeddingModel delegate,
            @ModelName("fallback") EmbeddingModel fallback,
            EmbeddingGuard guard,
            ModelFallbackConfig config) {
        this.delegate = delegate;
        this.fallback = fallback;
        this.guard = guard;
        this.config = config;
        Log.info("FallbackEmbeddingModel initialized with @ModelName(\"primary\") delegate");
    }

    @Override
    public Response<Embedding> embed(String text) {
        return executeWithFallback(() -> delegate.embed(text), () -> fallback.embed(text));
    }

    @Override
    public Response<Embedding> embed(TextSegment segment) {
        return executeWithFallback(() -> delegate.embed(segment), () -> fallback.embed(segment));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        return executeWithFallback(
                () -> delegate.embedAll(textSegments),
                () -> fallback.embedAll(textSegments));
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }

    private <T> T executeWithFallback(Supplier<T> primary, Supplier<T> fallbackCall) {
        if (!config.fallbackEnabled()) {
            return primary.get();
        }
        return guard.call(primary, fallbackCall);
    }
}
