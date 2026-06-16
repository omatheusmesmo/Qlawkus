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

import java.time.Duration;
import java.util.List;

@Alternative
@Priority(1)
@ApplicationScoped
public class FallbackEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final EmbeddingModel fallback;
    private final CircuitBreaker circuitBreaker;
    private final ModelFallbackConfig config;

    @Inject
    public FallbackEmbeddingModel(
            @PrimaryEmbedding EmbeddingModel delegate,
            @ModelName("fallback") EmbeddingModel fallback,
            CircuitBreaker circuitBreaker,
            ModelFallbackConfig config) {
        this.delegate = delegate;
        this.fallback = fallback;
        this.circuitBreaker = circuitBreaker;
        this.config = config;
        Log.info("FallbackEmbeddingModel initialized with @ModelName(\"primary\") delegate");
    }

    @Override
    public Response<Embedding> embed(String text) {
        return executeWithFallback(() -> delegate.embed(text), () -> fallback.embed(text), "embed");
    }

    @Override
    public Response<Embedding> embed(TextSegment segment) {
        return executeWithFallback(() -> delegate.embed(segment), () -> fallback.embed(segment), "embed(segment)");
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        return executeWithFallback(
                () -> delegate.embedAll(textSegments),
                () -> fallback.embedAll(textSegments),
                "embedAll");
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }

    private <T> T executeWithFallback(java.util.function.Supplier<T> primaryCall,
            java.util.function.Supplier<T> fallbackCall, String operation) {
        if (!config.fallbackEnabled()) {
            return primaryCall.get();
        }

        if (circuitBreaker.isOpen()) {
            Log.infof("Circuit breaker OPEN — routing %s to Ollama fallback", operation);
            return fallbackCall.get();
        }

        List<Duration> delays = config.retryDelaysAsDurations();
        Exception lastException = null;

        try {
            T result = primaryCall.get();
            if (circuitBreaker.isHalfOpen()) {
                circuitBreaker.recordSuccess();
            }
            return result;
        } catch (Exception e) {
            lastException = e;
            if (!isRetryable(e)) {
                throw e;
            }
        }

        for (int attempt = 0; attempt < delays.size(); attempt++) {
            Duration delay = delays.get(attempt);
            Log.warnf("EmbeddingModel %s attempt %d/%d failed: %s — retrying in %ds",
                    operation, attempt + 1, delays.size() + 1, lastException.getMessage(), delay.toSeconds());
            sleep(delay);

            try {
                T result = primaryCall.get();
                if (circuitBreaker.isHalfOpen()) {
                    circuitBreaker.recordSuccess();
                }
                return result;
            } catch (Exception e) {
                lastException = e;
            }
        }

        Log.warnf("All %d embedding attempts failed — opening circuit breaker and falling back to Ollama",
                delays.size() + 1);
        circuitBreaker.recordFailure();

        try {
            return fallbackCall.get();
        } catch (Exception fallbackEx) {
            Log.errorf(fallbackEx, "Fallback Ollama embedding also failed");
            throw new RuntimeException("Primary and fallback embedding models both failed. Primary: "
                    + lastException.getMessage() + ". Fallback: " + fallbackEx.getMessage(), fallbackEx);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry backoff", e);
        }
    }

    private boolean isRetryable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String className = current.getClass().getName();
            if (className.contains("NonRetriableException")
                    || className.contains("AuthenticationException")
                    || className.contains("ModelNotFoundException")
                    || className.contains("InvalidRequestException")
                    || className.contains("ContentFilteredException")
                    || className.contains("UnsupportedFeatureException")) {
                return false;
            }
            current = current.getCause();
        }
        return true;
    }
}
