package dev.omatheusmesmo.qlawkus.model;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.List;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.model")
public interface ModelFallbackConfig {

    /**
     * Comma-separated retry delays in seconds between attempts before falling back.
     */
    @WithDefault("30,60,120")
    List<Integer> retryDelays();

    /**
     * Seconds the circuit breaker stays OPEN before transitioning to HALF_OPEN.
     */
    @WithDefault("300")
    int circuitBreakerResetTimeout();

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
            @WithDefault("gemma4:e2b")
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

    default Duration resetTimeout() {
        return Duration.ofSeconds(circuitBreakerResetTimeout());
    }

    default List<Duration> retryDelaysAsDurations() {
        return retryDelays().stream()
                .map(Duration::ofSeconds)
                .toList();
    }
}
