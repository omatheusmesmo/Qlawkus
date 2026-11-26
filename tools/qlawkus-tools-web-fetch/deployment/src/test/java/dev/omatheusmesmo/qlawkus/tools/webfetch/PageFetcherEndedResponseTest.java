package dev.omatheusmesmo.qlawkus.tools.webfetch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * A small error response can end before the callback that discards it runs, and Vert.x then refuses
 * every new handler with an {@code IllegalStateException}.
 */
class PageFetcherEndedResponseTest {

    private final AtomicInteger resets = new AtomicInteger();
    private final HttpClientRequest request = proxy(HttpClientRequest.class, (method, args) -> {
        if (method.getName().equals("reset")) {
            resets.incrementAndGet();
            return Future.succeededFuture();
        }
        return null;
    });

    @Test
    void discardingAResponseThatAlreadyEndedIsNotAnError() {
        HttpClientResponse ended = proxy(HttpClientResponse.class, (method, args) -> {
            if (method.getName().equals("exceptionHandler")) {
                throw new IllegalStateException("Response already ended");
            }
            return method.getName().equals("request") ? request : null;
        });

        assertDoesNotThrow(() -> PageFetcher.discard(ended));
        assertEquals(0, resets.get(), "an ended response has nothing left to reset");
    }

    @Test
    void discardingALiveResponseResetsItsRequest() {
        HttpClientResponse live = proxy(HttpClientResponse.class,
                (method, args) -> method.getName().equals("request") ? request : null);

        PageFetcher.discard(live);

        assertEquals(1, resets.get());
    }

    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (self, method, args) -> {
                    Object result = call.invoke(method, args);
                    if (result == null && method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return result;
                }));
    }

    @FunctionalInterface
    private interface Call {
        Object invoke(Method method, Object[] args);
    }
}
