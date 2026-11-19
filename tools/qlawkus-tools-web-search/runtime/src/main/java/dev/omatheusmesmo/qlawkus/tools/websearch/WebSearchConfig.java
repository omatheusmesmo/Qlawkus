package dev.omatheusmesmo.qlawkus.tools.websearch;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

/** Configuration for the optional, key-free web search capability. */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.web-search")
public interface WebSearchConfig {
    /** Allow web searches. Disabled tools never send requests. */
    @WithDefault("false")
    boolean enabled();

    /** Trusted SearXNG instance with JSON search enabled, e.g. http://searxng:8080. */
    Optional<String> baseUrl();

    /** Connection and read timeout, rounded up to whole seconds and clamped to 1–60 seconds. */
    @WithDefault("10s")
    Duration timeout();

    /** Maximum results per search, clamped to the range 1–20. */
    @WithDefault("5")
    int maxResults();

    /** Maximum output characters including framing, clamped to the range 512–64000. */
    @WithDefault("8000")
    int maxChars();
}
