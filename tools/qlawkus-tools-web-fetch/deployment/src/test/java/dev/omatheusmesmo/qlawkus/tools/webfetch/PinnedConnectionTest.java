package dev.omatheusmesmo.qlawkus.tools.webfetch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.vertx.core.Vertx;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The address {@link FetchPolicy} validated is the one the connection uses. {@code rebind.test} resolves
 * nowhere, so a client that looked the name up again would fail to connect; the policy here vouches for
 * the loopback test server instead, and the page can only arrive through that vouched address.
 */
class PinnedConnectionTest {

    private static final String HOST = "rebind.test";

    private static HttpServer server;
    private static Vertx vertx;
    private static final AtomicReference<String> hostHeader = new AtomicReference<>();

    @BeforeAll
    static void start() throws IOException {
        vertx = Vertx.vertx();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            hostHeader.set(exchange.getRequestHeaders().getFirst("Host"));
            byte[] body = "pinned".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
        vertx.close();
    }

    @Test
    void theConnectionGoesToTheValidatedAddressWithoutLookingTheNameUpAgain() {
        PageFetcher fetcher = new PageFetcher(new StubConfig(), vouchingForLoopback(), vertx);
        URI uri = URI.create("http://" + HOST + ":" + server.getAddress().getPort() + "/page");

        PageFetcher.Page page = fetcher.fetch(uri);

        assertEquals("pinned", new String(page.body(), StandardCharsets.UTF_8));
        assertEquals(HOST + ":" + server.getAddress().getPort(), hostHeader.get());
    }

    @Test
    void aNameTheSystemCannotResolveStillFailsWhenThePolicyDoesNotVouchForIt() {
        FetchPolicy unresolvable = new FetchPolicy(List.of(), true);
        PageFetcher fetcher = new PageFetcher(new StubConfig(), unresolvable, vertx);

        FetchFailure failure = assertThrows(FetchFailure.class,
                () -> fetcher.fetch(URI.create("http://" + HOST + "/page")));

        assertTrue(failure.getMessage().contains("Could not resolve"));
    }

    private static FetchPolicy vouchingForLoopback() {
        return new FetchPolicy(List.of(), true) {
            @Override
            public InetAddress[] resolve(URI uri) {
                return new InetAddress[] {InetAddress.getLoopbackAddress()};
            }
        };
    }

    private static final class StubConfig implements WebFetchConfig {

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public Duration timeout() {
            return Duration.ofSeconds(5);
        }

        @Override
        public long maxResponseBytes() {
            return 1_048_576;
        }

        @Override
        public int maxChars() {
            return 20_000;
        }

        @Override
        public int maxRedirects() {
            return 3;
        }

        @Override
        public Optional<List<String>> blockedDomains() {
            return Optional.empty();
        }

        @Override
        public String userAgent() {
            return "test";
        }
    }
}
