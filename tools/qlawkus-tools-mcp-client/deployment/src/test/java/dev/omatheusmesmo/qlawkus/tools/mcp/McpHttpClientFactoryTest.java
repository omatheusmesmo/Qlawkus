package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.http.client.HttpClientBuilderFactory;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * Every LangChain4j HTTP client is built through one service-loaded factory, and the loader refuses to
 * pick when several are present. The MCP client must therefore not bring the JDK factory next to the
 * JAX-RS one Quarkus uses, or any other extension that builds an HTTP client (web search) fails at its
 * first use.
 */
class McpHttpClientFactoryTest {
    @Test
    void exactlyOneHttpClientBuilderFactoryIsOnTheClasspath() {
        var factories = ServiceLoader.load(HttpClientBuilderFactory.class).stream()
                .map(provider -> provider.type().getName()).toList();
        assertEquals(1, factories.size(), "HTTP client factories: " + factories);
    }
}
