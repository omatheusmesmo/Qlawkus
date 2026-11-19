package dev.omatheusmesmo.qlawkus.tools.websearch;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchResults;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WebSearchGateTest {
    @Test
    void disabledDoesNotCallEngine() {
        WebSearchTool tool = new WebSearchTool(new TestConfig(false, Optional.empty()), request -> {
            fail("Disabled search must not call its engine");
            return null;
        });
        assertTrue(tool.searchWeb("Java").contains("enabled=false"));
    }

    @Test
    void customEngineRequiresNoSearxngConfiguration() {
        AtomicInteger calls = new AtomicInteger();
        WebSearchTool tool = new WebSearchTool(new TestConfig(true, Optional.empty()), request -> {
            calls.incrementAndGet();
            assertEquals(5, request.maxResults());
            return WebSearchResults.from(WebSearchInformationResult.from(0L), List.of());
        });
        assertTrue(tool.searchWeb("Java").contains("No results found."));
        assertEquals(1, calls.get());
    }

    @Test
    void missingDefaultEndpointIsReportedWithoutNetwork() {
        TestConfig config = new TestConfig(true, Optional.empty());
        WebSearchTool tool = new WebSearchTool(config, new WebSearchEngineProducer().engine(config));
        assertTrue(tool.searchWeb("Java").contains("qlawkus.web-search.base-url"));
    }

    @Test
    void validatesTrustedEndpointWithoutEchoingCredentials() {
        for (String url : List.of("file:///etc", "http://user:secret@host/", "http://host/?q=1", "http://host/#f", "http://", "invalid")) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> WebSearchEngineProducer.validateBaseUrl(url));
            assertFalse(error.getMessage().contains("secret"));
        }
        assertEquals("https://search.example/path", WebSearchEngineProducer.validateBaseUrl("https://search.example/path/"));
        assertEquals("http://searxng:8080", WebSearchEngineProducer.validateBaseUrl("http://searxng:8080"));
    }

    @Test
    void timeoutNeverBecomesUnlimitedInUpstreamTransport() {
        assertEquals(Duration.ofSeconds(1), WebSearchEngineProducer.timeout(Duration.ofMillis(200)));
        assertEquals(Duration.ofSeconds(2), WebSearchEngineProducer.timeout(Duration.ofMillis(1200)));
        assertEquals(Duration.ofSeconds(60), WebSearchEngineProducer.timeout(Duration.ofHours(1)));
        assertEquals(Duration.ofSeconds(1), WebSearchEngineProducer.timeout(Duration.ZERO));
    }

    private record TestConfig(boolean enabled, Optional<String> baseUrl) implements WebSearchConfig {
        public Duration timeout() { return Duration.ofSeconds(1); }
        public int maxResults() { return 5; }
        public int maxChars() { return 8000; }
    }
}
