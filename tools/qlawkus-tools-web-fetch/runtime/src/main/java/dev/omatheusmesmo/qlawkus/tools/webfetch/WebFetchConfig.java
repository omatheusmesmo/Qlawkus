package dev.omatheusmesmo.qlawkus.tools.webfetch;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Configuration for the optional capability that reads a web page on the owner's behalf. */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.web-fetch")
public interface WebFetchConfig {
    /** Allow the agent to fetch URLs. A disabled tool never opens a connection. */
    @WithDefault("false")
    boolean enabled();

    /** Per-request timeout, clamped to 1 to 120 seconds. A timed-out request is not retried. */
    @WithDefault("30s")
    Duration timeout();

    /** Largest response body read, in bytes, clamped to 1 KiB to 10 MiB. A longer body is cut and marked. */
    @WithDefault("1048576")
    long maxResponseBytes();

    /** Largest result handed to the model, framing included, clamped to 512 to 200000 characters. */
    @WithDefault("20000")
    int maxChars();

    /** Redirect hops followed, each checked as strictly as the first request, clamped to 0 to 10. */
    @WithDefault("3")
    int maxRedirects();

    /** Domains never fetched. A listed domain also blocks its subdomains; an address literal blocks that address. */
    Optional<List<String>> blockedDomains();

    /** The {@code User-Agent} sent, browser-like by default so ordinary sites do not turn the request away. */
    @WithDefault("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36")
    String userAgent();
}
