package dev.omatheusmesmo.qlawkus;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
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
    WebClient client = WebClient.create(vertx);

    String auth = Base64.getEncoder().encodeToString("admin:admin123".getBytes());
    String body = "{\"message\": \"What is your name? Reply briefly.\"}";

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<StringBuilder> collected = new AtomicReference<>(new StringBuilder());
    AtomicReference<Integer> statusCode = new AtomicReference<>();

    client
      .post(url.getPort(), url.getHost(), "/api/chat")
      .putHeader("Authorization", "Basic " + auth)
      .putHeader("Content-Type", "application/json")
      .sendBuffer(Buffer.buffer(body))
      .onSuccess(response -> {
        statusCode.set(response.statusCode());
        String bodyContent = response.bodyAsString();
        collected.get().append(bodyContent);
        latch.countDown();
      })
      .onFailure(err -> {
        collected.get().append("ERROR: ").append(err.getMessage());
        latch.countDown();
      });

    assertTrue(latch.await(120, TimeUnit.SECONDS), "SSE stream should complete within timeout");

    client.close();
    vertx.close();

    assertEquals(200, statusCode.get(), "Should return 200 with valid auth");
    String result = collected.get().toString();
    assertFalse(result.isBlank(), "SSE stream should produce content");

    String plainText = result.replace("data:", "").replace("\n", "").trim();
    assertTrue(plainText.toLowerCase().contains("qlawkus"),
      "SSE response should contain the soul name 'Qlawkus'. Got: " + result);
  }

  @Test
  void chat_stream_withoutAuth_returns401() throws InterruptedException {
    Vertx vertx = Vertx.vertx();
    WebClient client = WebClient.create(vertx);

    String body = "{\"message\": \"hello\"}";

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Integer> statusCode = new AtomicReference<>();

    client
      .post(url.getPort(), url.getHost(), "/api/chat")
      .putHeader("Content-Type", "application/json")
      .sendBuffer(Buffer.buffer(body))
      .onSuccess(response -> {
        statusCode.set(response.statusCode());
        latch.countDown();
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
