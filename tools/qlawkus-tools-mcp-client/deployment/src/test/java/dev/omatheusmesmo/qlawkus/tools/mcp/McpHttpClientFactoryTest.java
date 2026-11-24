package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderFactory;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

/**
 * langchain4j-mcp brings the JDK HTTP client factory, because its transport names the JDK client in a
 * method body and a native image cannot link that without the class. The LangChain4j loader picks the
 * factory with the highest priority and refuses only a tie, so the JAX-RS factory Quarkus uses must keep
 * winning, or every other builder of an HTTP client (web search) would silently use the JDK one.
 */
class McpHttpClientFactoryTest {
    @Test
    void theJaxRsFactoryOutranksEveryOtherFactoryOnTheClasspath() {
        var factories = ServiceLoader.load(HttpClientBuilderFactory.class).stream()
                .map(ServiceLoader.Provider::get).toList();
        assertTrue(factories.size() > 1, "the JDK factory is expected next to the JAX-RS one: " + factories);

        int highest = factories.stream().mapToInt(HttpClientBuilderFactory::priority).max().orElseThrow();
        var winners = factories.stream().filter(factory -> factory.priority() == highest).toList();
        assertEquals(1, winners.size(), "the loader refuses a tie between factories: " + winners);

        HttpClientBuilder builder = HttpClientBuilderLoader.loadHttpClientBuilder();
        assertEquals(JaxRsHttpClientBuilder.class, builder.getClass());
    }
}
