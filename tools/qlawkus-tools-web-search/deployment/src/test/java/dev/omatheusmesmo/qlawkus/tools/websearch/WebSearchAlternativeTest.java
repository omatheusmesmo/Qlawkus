package dev.omatheusmesmo.qlawkus.tools.websearch;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Boots with no key or SearXNG URL and replaces the default engine through its standard CDI SPI. */
@QuarkusTest
@TestProfile(WebSearchAlternativeTest.Profile.class)
class WebSearchAlternativeTest {
    @Inject @QlawTool WebSearchTool tool;
    @Inject WebSearchConfig config;

    @Test
    void replacesDefaultEngineWithoutSearxngEndpointOrCredentials() {
        assertTrue(config.baseUrl().isEmpty());
        assertTrue(tool.searchWeb("test").contains("Alternative engine"));
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(AlternativeEngine.class);
        }
    }

    @Alternative
    @ApplicationScoped
    public static class AlternativeEngine implements WebSearchEngine {
        @Override
        public WebSearchResults search(WebSearchRequest request) {
            return WebSearchResults.from(WebSearchInformationResult.from(1L), List.of(
                    WebSearchOrganicResult.from("Alternative engine", URI.create("https://example.com/"),
                            "A pluggable upstream search engine", null, Map.of())));
        }
    }
}
