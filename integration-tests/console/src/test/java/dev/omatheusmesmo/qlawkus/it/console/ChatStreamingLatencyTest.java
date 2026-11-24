package dev.omatheusmesmo.qlawkus.it.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The console renders the reply as it arrives, so the SSE chunks must reach the client while the model is still
 * answering, not after the whole completion has been read. The model stub speaks the OpenAI-compatible streaming
 * format of the real provider and releases its body over three seconds; a pipeline that buffers the turn delivers
 * everything at the end and fails the spread bound.
 */
@QuarkusTest
@ConnectWireMock
class ChatStreamingLatencyTest {

  private static final int TOKENS = 6;
  private static final int MODEL_MILLIS = 3000;

  WireMock wiremock;

  @TestHTTPResource
  URL url;

  @BeforeEach
  void stubModel() {
    wiremock.register(WireMock.post(WireMock.urlEqualTo("/v1/chat/completions"))
        .withRequestBody(WireMock.containing("\"stream\""))
        .atPriority(1)
        .willReturn(WireMock.aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "text/event-stream")
            .withChunkedDribbleDelay(TOKENS + 1, MODEL_MILLIS)
            .withBody(slowSse())));
    wiremock.register(WireMock.post(WireMock.urlEqualTo("/v1/embeddings"))
        .willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
            .withBody("{\"data\":[{\"embedding\":" + vector(1024)
                + ",\"index\":0}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}")));
  }

  @Test
  void theFirstChunkArrivesWhileTheModelIsStillAnswering() throws InterruptedException {
    Vertx vertx = Vertx.vertx();
    HttpClient client = vertx.createHttpClient();
    CountDownLatch done = new CountDownLatch(1);
    List<Long> arrivals = new CopyOnWriteArrayList<>();
    AtomicInteger status = new AtomicInteger();
    String auth = Base64.getEncoder().encodeToString("qlawkus:qlawkus-test".getBytes());

    long started = System.nanoTime();
    client.request(HttpMethod.POST, url.getPort(), url.getHost(), "/api/chat")
        .onSuccess(request -> {
          request.putHeader("Authorization", "Basic " + auth);
          request.putHeader("Content-Type", "application/json");
          request.putHeader("Accept", "text/event-stream");
          request.send("{\"message\": \"Say something slowly.\"}").onSuccess(response -> {
            status.set(response.statusCode());
            response.handler(chunk -> {
              if (chunk.toString().contains("data:")) {
                arrivals.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
              }
            });
            response.exceptionHandler(failure -> done.countDown());
            response.endHandler(end -> done.countDown());
          }).onFailure(failure -> done.countDown());
        })
        .onFailure(failure -> done.countDown());

    assertTrue(done.await(60, TimeUnit.SECONDS), "the stream must complete");
    client.close();
    vertx.close();

    assertEquals(200, status.get());
    assertTrue(arrivals.size() >= 2, "the reply arrives in several chunks, not one: " + arrivals);
    long first = arrivals.get(0);
    long last = arrivals.get(arrivals.size() - 1);
    assertTrue(last - first >= MODEL_MILLIS / 3,
        "the chunks are spread over the model's answer instead of delivered together: " + arrivals);
  }

  private static String slowSse() {
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < TOKENS; i++) {
      body.append("data: {\"id\":\"chatcmpl-slow\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,")
          .append("\"model\":\"slow\",\"choices\":[{\"index\":0,\"delta\":{")
          .append(i == 0 ? "\"role\":\"assistant\"," : "")
          .append("\"content\":\"word").append(i).append(" \"},\"finish_reason\":null}]}\n\n");
    }
    body.append("data: {\"id\":\"chatcmpl-slow\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,")
        .append("\"model\":\"slow\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n")
        .append("data: [DONE]\n\n");
    return body.toString();
  }

  private static String vector(int dimension) {
    StringBuilder embedding = new StringBuilder("[");
    for (int i = 0; i < dimension; i++) {
      if (i > 0) {
        embedding.append(",");
      }
      embedding.append("0.01");
    }
    return embedding.append("]").toString();
  }
}
