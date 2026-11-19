package dev.omatheusmesmo.qlawkus.tools.websearch;

import dev.langchain4j.community.web.search.searxng.SearXNGWebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngine;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/** Supplies upstream SearXNG; distributions may replace the standard WebSearchEngine CDI bean. */
@ApplicationScoped
public class WebSearchEngineProducer {
    @Produces
    @Singleton
    @DefaultBean
    WebSearchEngine engine(WebSearchConfig config) {
        if (config.baseUrl().isEmpty()) {
            return request -> {
                throw new IllegalStateException("Configure qlawkus.web-search.base-url with a trusted SearXNG instance.");
            };
        }
        return SearXNGWebSearchEngine.builder()
                .baseUrl(validateBaseUrl(config.baseUrl().orElseThrow()))
                .duration(timeout(config.timeout()))
                .build();
    }

    /** Quarkiverse 1.15 rounds down to seconds; never let a subsecond timeout become unlimited. */
    static Duration timeout(Duration configured) {
        if (configured.isNegative() || configured.isZero()) {
            return Duration.ofSeconds(1);
        }
        if (configured.compareTo(Duration.ofSeconds(60)) >= 0) {
            return Duration.ofSeconds(60);
        }
        return Duration.ofSeconds(Math.max(1, configured.getSeconds() + (configured.getNano() > 0 ? 1 : 0)));
    }

    static String validateBaseUrl(String value) {
        try {
            URI uri = URI.create(value.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri.toString().replaceAll("/+$", "");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("qlawkus.web-search.base-url must be an http(s) base URL without credentials, query or fragment.");
        }
    }
}
