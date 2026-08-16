package dev.omatheusmesmo.qlawkus.model;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.model")
public interface ModelFallbackConfig {

    /**
     * Circuit breaker names, one per upstream API surface. {@code io.smallrye.faulttolerance.api.
     * TypedGuard} registers a breaker's state under its {@code name()} exactly once - a second guard
     * built with the same name fails with "Circuit breaker already exists" - so a single breaker
     * cannot be shared across the independently-built chat, streaming and embedding guards. Chat and
     * streaming chat share one breaker anyway (same completions endpoint, sync vs. streamed transport);
     * embedding gets its own, since a provider incident on one surface does not imply the other is down
     * too. {@link dev.omatheusmesmo.qlawkus.health.ModelReadinessCheck} reads both by name through
     * {@code CircuitBreakerMaintenance}, without depending on any of the guards directly.
     */
    String CIRCUIT_BREAKER_CHAT = "qlawkus-primary-chat";

    /**
     * @see #CIRCUIT_BREAKER_CHAT
     */
    String CIRCUIT_BREAKER_EMBEDDING = "qlawkus-primary-embedding";

    /**
     * Number of retries against the primary model before falling back to Ollama.
     */
    @WithDefault("3")
    int retryMaxAttempts();

    /**
     * Delay before the first retry.
     */
    @WithDefault("30s")
    Duration retryInitialDelay();

    /**
     * Delay cap the exponential backoff between retries will not exceed.
     */
    @WithDefault("120s")
    Duration retryMaxDelay();

    /**
     * How long the circuit breaker stays OPEN before allowing a HALF_OPEN probe against the primary.
     */
    @WithDefault("300s")
    Duration circuitBreakerResetTimeout();

    /**
     * Whether the fallback to Ollama is enabled.
     */
    @WithDefault("true")
    boolean fallbackEnabled();

    /**
     * Fallback configuration.
     */
    Fallback fallback();

    /**
     * Fallback settings.
     */
    interface Fallback {

        /**
         * Ollama fallback configuration.
         */
        Ollama ollama();

        /**
         * Ollama fallback settings.
         */
        interface Ollama {

            /**
             * Base URL of the Ollama fallback server.
             */
            @WithDefault("http://localhost:11434")
            String baseUrl();

            /**
             * Chat model name for Ollama fallback.
             */
            @WithDefault("qwen3.5:4b")
            String chatModel();

            /**
             * Embedding model name for Ollama fallback.
             */
            @WithDefault("mxbai-embed-large")
            String embeddingModel();

            /**
             * Timeout for Ollama fallback requests.
             */
            @WithDefault("120s")
            Duration timeout();
        }
    }
}
