package dev.omatheusmesmo.qlawkus.rest;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import org.junit.jupiter.api.BeforeEach;
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
@ConnectWireMock
@TestProfile(WireMockSseProfile.class)
class ApiResourceSseTest {

  WireMock wiremock;

  @TestHTTPResource
  URL url;

  @BeforeEach
  void setupStubs() {
    wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat"))
        .willReturn(WireMock.aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/x-ndjson")
            .withBody(chatNdjsonResponse())));

    wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/embed"))
        .willReturn(WireMock.aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(embedJsonResponse())));
  }

  @Test
  void chat_stream_returnsSseTokens() throws InterruptedException {
    Vertx vertx = Vertx.vertx();
    HttpClient client = vertx.createHttpClient();

    CountDownLatch latch = new CountDownLatch(1);
    StringBuilder collected = new StringBuilder();
    AtomicReference<Integer> statusCode = new AtomicReference<>();

    String auth = Base64.getEncoder().encodeToString("qlawkus:qlawkus-test".getBytes());

    client.request(HttpMethod.POST, url.getPort(), url.getHost(), "/api/chat")
      .onSuccess(request -> {
        request.putHeader("Authorization", "Basic " + auth);
        request.putHeader("Content-Type", "application/json");
        request.putHeader("Accept", "text/event-stream");

          request.send("{\"message\": \"What is your name?\"}")
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

    assertTrue(latch.await(30, TimeUnit.SECONDS), "SSE stream should complete within timeout");

    client.close();
    vertx.close();

    assertEquals(200, statusCode.get(), "Should return 200 with valid auth");
    String result = collected.toString();
    assertFalse(result.isBlank(), "SSE stream should produce content");
    assertTrue(result.startsWith("data:"), "SSE response should use data: prefix. Got: " + result);
  }

  private static String chatNdjsonResponse() {
    String ts = "2026-04-25T00:00:00Z";
    return "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts
        + "\",\"message\":{\"role\":\"assistant\",\"content\":\"Qlawkus\"},\"done\":false}\n"
        + "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts
        + "\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"total_duration\":0,\"eval_count\":1}\n";
  }

  private static String embedJsonResponse() {
    StringBuilder embedding = new StringBuilder("[");
    for (int i = 0; i < 768; i++) {
      if (i > 0)
        embedding.append(",");
      embedding.append("0.01");
    }
    embedding.append("]");
    return "{\"model\":\"nomic-embed-text\",\"embeddings\":[" + embedding + "],\"total_duration\":0}";
  }
}
