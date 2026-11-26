package dev.omatheusmesmo.qlawkus.tools.mcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.sse.DefaultServerSentEventParser;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientAgent;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Vert.x refuses to attach a handler to a response that already ended or failed, which is what a
 * cancelled or closed exchange leaves behind when its response callback finally runs.
 */
class McpHttpClientEndedResponseTest {

    private final Promise<HttpClientResponse> sent = Promise.promise();
    private final AtomicInteger resets = new AtomicInteger();
    private final HttpClientRequest request = proxy(HttpClientRequest.class, (self, method, args) -> {
        return switch (method.getName()) {
            case "putHeader" -> self;
            case "send" -> sent.future();
            case "reset" -> {
                resets.incrementAndGet();
                yield Future.succeededFuture();
            }
            default -> defaultFor(method.getReturnType());
        };
    });
    private final HttpClientResponse endedResponse = proxy(HttpClientResponse.class, (self, method, args) -> {
        return switch (method.getName()) {
            case "exceptionHandler", "handler", "endHandler" -> throw new IllegalStateException("Response already ended");
            case "statusCode" -> 200;
            case "request" -> request;
            default -> defaultFor(method.getReturnType());
        };
    });
    private final HttpClientAgent agent = proxy(HttpClientAgent.class, (self, method, args) -> {
        return switch (method.getName()) {
            case "request" -> Future.succeededFuture(request);
            case "close" -> Future.succeededFuture();
            default -> defaultFor(method.getReturnType());
        };
    });
    private final McpHttpClient client = new McpHttpClient(agent, Duration.ofSeconds(5));

    @Test
    void aResponseThatEndedAfterTheExchangeWasCancelledIsDroppedQuietly() {
        Observer observer = subscribe();
        observer.subscription.get().cancel();

        assertDoesNotThrow(() -> sent.complete(endedResponse));

        assertEquals(2, resets.get(), "the cancel resets the request and the late response resets it again");
        assertEquals(0, observer.events.get());
    }

    @Test
    void aResponseThatEndedBeforeItCouldBeReadFailsTheExchangeInsteadOfHangingIt() throws Exception {
        Observer observer = subscribe();

        sent.complete(endedResponse);

        assertTrue(observer.done.await(5, TimeUnit.SECONDS), "the exchange must terminate");
        assertInstanceOf(IOException.class, observer.error.get());
        assertEquals(0, observer.events.get());
    }

    private Observer subscribe() {
        Observer observer = new Observer();
        HttpRequest get = HttpRequest.builder().method(HttpMethod.GET).url("http://127.0.0.1:1/mcp").build();
        client.stream(get, new DefaultServerSentEventParser()).subscribe(observer);
        return observer;
    }

    private static final class Observer implements Flow.Subscriber<HttpStreamingEvent> {
        final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicInteger events = new AtomicInteger();
        final CountDownLatch done = new CountDownLatch(1);

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription.set(value);
            value.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(HttpStreamingEvent event) {
            events.incrementAndGet();
        }

        @Override
        public void onError(Throwable failure) {
            error.set(failure);
            done.countDown();
        }

        @Override
        public void onComplete() {
            done.countDown();
        }
    }

    private static <T> T proxy(Class<T> type, Handler handler) {
        InvocationHandler invocation = (self, method, args) -> handler.handle(self, method, args);
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, invocation));
    }

    private static Object defaultFor(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    @FunctionalInterface
    private interface Handler {
        Object handle(Object self, Method method, Object[] args) throws Throwable;
    }
}
