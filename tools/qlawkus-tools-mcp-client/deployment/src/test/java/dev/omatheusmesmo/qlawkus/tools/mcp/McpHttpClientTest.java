package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The streaming contract the upstream MCP transport relies on, against a real loopback server. */
class McpHttpClientTest {
    private Vertx vertx;
    private HttpServer server;
    private McpHttpClient client;
    private final AtomicInteger redirectTargetHits = new AtomicInteger();
    private int port;

    @BeforeEach
    void start() throws Exception {
        vertx = Vertx.vertx();
        server = vertx.createHttpServer().requestHandler(request -> {
            switch (request.path()) {
                case "/events" -> {
                    StringBuilder body = new StringBuilder();
                    for (int i = 0; i < 200; i++) body.append("event: message\ndata: ").append(i).append("\n\n");
                    request.response().putHeader("Content-Type", "text/event-stream")
                            .putHeader("Mcp-Session-Id", "abc").end(body.toString());
                }
                case "/fail" -> request.response().setStatusCode(500).end("x".repeat(McpHttpClient.ERROR_BODY_LIMIT * 2));
                case "/redirect" -> request.response().setStatusCode(307)
                        .putHeader("Location", "http://127.0.0.1:" + port + "/target").end();
                case "/target" -> {
                    redirectTargetHits.incrementAndGet();
                    request.response().end("reached");
                }
                case "/echo" -> request.body().onSuccess(body -> request.response()
                        .putHeader("Content-Type", "text/event-stream")
                        .end("data: " + request.getHeader("X-Probe") + request.method().name() + body + "\n\n"));
                default -> request.response().setStatusCode(404).end();
            }
        }).listen(0, "127.0.0.1").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        port = server.actualPort();
        client = (McpHttpClient) new McpHttpClientBuilder(vertx, new McpEndpointPolicy(true))
                .connectTimeout(Duration.ofSeconds(2)).build();
    }

    @AfterEach
    void stop() throws Exception {
        client.close();
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private HttpRequest get(String path) {
        return HttpRequest.builder().method(HttpMethod.GET).url("http://127.0.0.1:" + port + path).build();
    }

    private Outcome run(HttpRequest request, long perDemand) throws Exception {
        Outcome outcome = new Outcome(perDemand);
        client.stream(request, new DefaultServerSentEventParser()).subscribe(outcome);
        assertTrue(outcome.done.await(10, TimeUnit.SECONDS), "the stream must terminate");
        return outcome;
    }

    @Test
    void theResponseHeadComesFirstThenEveryEventInOrderEvenWhenDemandIsOneByOne() throws Exception {
        Outcome outcome = run(get("/events"), 1);

        assertInstanceOf(HttpResponseReceived.class, outcome.events.get(0));
        var head = (HttpResponseReceived) outcome.events.get(0);
        assertEquals(200, head.response().statusCode());
        assertEquals("abc", head.response().headers().get("mcp-session-id").get(0));
        assertTrue(head.response().contentType().contains("text/event-stream"));
        assertEquals(201, outcome.events.size());
        for (int i = 0; i < 200; i++) {
            assertEquals(String.valueOf(i), ((ServerSentEvent) outcome.events.get(i + 1)).data());
        }
        assertEquals(null, outcome.error);
    }

    @Test
    void anErrorStatusIsReportedAsSoonAsItIsKnownThenFailsWithABoundedBody() throws Exception {
        Outcome outcome = run(get("/fail"), Long.MAX_VALUE);

        var error = assertInstanceOf(HttpErrorResponseReceived.class, outcome.events.get(0));
        assertEquals(500, error.statusCode());
        var failure = assertInstanceOf(HttpException.class, outcome.error);
        assertEquals(500, failure.statusCode());
        assertEquals(McpHttpClient.ERROR_BODY_LIMIT, failure.getMessage().length());
    }

    @Test
    void aRedirectIsNeverFollowed() throws Exception {
        Outcome outcome = run(get("/redirect"), Long.MAX_VALUE);

        assertEquals(307, assertInstanceOf(HttpErrorResponseReceived.class, outcome.events.get(0)).statusCode());
        assertEquals(0, redirectTargetHits.get(), "the redirect target must never receive a request");
    }

    @Test
    void methodHeadersAndBodyReachTheServer() throws Exception {
        HttpRequest post = HttpRequest.builder().method(HttpMethod.POST)
                .url("http://127.0.0.1:" + port + "/echo").addHeader("X-Probe", "p1").body("{\"a\":1}").build();

        Outcome outcome = run(post, Long.MAX_VALUE);

        assertEquals("p1POST{\"a\":1}", ((ServerSentEvent) outcome.events.get(1)).data());
    }

    @Test
    void cancellingStopsDeliveryWithoutATerminalSignal() throws Exception {
        Outcome outcome = new Outcome(1);
        outcome.cancelAfter = 3;
        client.stream(get("/events"), new DefaultServerSentEventParser()).subscribe(outcome);
        assertTrue(outcome.cancelled.await(10, TimeUnit.SECONDS));
        Thread.sleep(300);

        assertEquals(3, outcome.events.size());
        assertEquals(1, outcome.done.getCount(), "a cancelled stream must not complete");
    }

    private static final class Outcome implements Flow.Subscriber<HttpStreamingEvent> {
        final List<HttpStreamingEvent> events = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch done = new CountDownLatch(1);
        final CountDownLatch cancelled = new CountDownLatch(1);
        volatile Throwable error;
        volatile int cancelAfter = -1;
        private final long perDemand;
        private Flow.Subscription subscription;

        Outcome(long perDemand) {
            this.perDemand = perDemand;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(perDemand);
        }

        @Override
        public void onNext(HttpStreamingEvent event) {
            events.add(event);
            if (events.size() == cancelAfter) {
                subscription.cancel();
                cancelled.countDown();
                return;
            }
            subscription.request(perDemand);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            done.countDown();
        }

        @Override
        public void onComplete() {
            done.countDown();
        }
    }
}
