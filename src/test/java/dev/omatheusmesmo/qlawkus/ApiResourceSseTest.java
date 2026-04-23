package dev.omatheusmesmo.qlawkus;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ApiResourceSseTest {

  @TestHTTPResource
  URL url;

  @Test
  void chat_stream_returnsSseTokens() throws InterruptedException {
    Vertx vertx = Vertx.vertx();
    HttpClient client = vertx.createHttpClient();

    String auth = Base64.getEncoder().encodeToString("admin:admin123".getBytes());

    CountDownLatch latch = new CountDownLatch(1);
    StringBuilder collected = new StringBuilder();
    AtomicReference<Integer> statusCode = new AtomicReference<>();

    client.request(HttpMethod.POST, url.getPort(), url.getHost(), "/api/chat")
        .onSuccess(request -> {
          request.putHeader("Authorization", "Basic " + auth);
          request.putHeader("Content-Type", "application/json");
          request.putHeader("Accept", "text/event-stream");

          request.send("{\"message\": \"What is your name? Reply briefly.\"}")
              .onSuccess(response -> {
                statusCode.set(response.statusCode());
                response.exceptionHandler(err -> latch.countDown());
                response.handler(chunk -> collected.append(chunk.toString()));
                response.endHandler(v -> latch.countDown());
              })
              .onFailure(err -> {
                collected.append("ERROR: ").append(err.getMessage());
                latch.countDown();
              });
        })
        .onFailure(err -> {
          collected.append("ERROR: ").append(err.getMessage());
          latch.countDown();
        });

    assertTrue(latch.await(120, TimeUnit.SECONDS), "SSE stream should complete within timeout");

    client.close();
    vertx.close();

    assertEquals(200, statusCode.get(), "Should return 200 with valid auth");
    String result = collected.toString();
    assertFalse(result.isBlank(), "SSE stream should produce content");
    assertTrue(result.startsWith("data:"), "SSE response should use data: prefix. Got: " + result);
  }

  @Test
  void chat_stream_withoutAuth_returns401() throws InterruptedException {
    Vertx vertx = Vertx.vertx();
    HttpClient client = vertx.createHttpClient();

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Integer> statusCode = new AtomicReference<>();

    client.request(HttpMethod.POST, url.getPort(), url.getHost(), "/api/chat")
        .onSuccess(request -> {
          request.putHeader("Content-Type", "application/json");
          request.send("{\"message\": \"hello\"}")
              .onSuccess(response -> {
                statusCode.set(response.statusCode());
                latch.countDown();
              })
              .onFailure(err -> {
                statusCode.set(-1);
                latch.countDown();
              });
        })
        .onFailure(err -> {
          statusCode.set(-1);
          latch.countDown();
        });

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    client.close();
    vertx.close();

    assertEquals(401, statusCode.get(), "Should return 401 without auth");
  }
}
