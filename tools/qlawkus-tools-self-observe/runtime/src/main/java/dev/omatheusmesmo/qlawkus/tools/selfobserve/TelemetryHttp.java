package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.faulttolerance.Retry;

/**
 * The one HTTP call the self-observation tools make: a GET to a backend's query API, with a URI the
 * tool built from configuration. Read only by construction (there is no other method) and never
 * follows a redirect, so a backend cannot send the request somewhere the configuration did not name.
 * <p>
 * Failures split the way the project's HTTP clients do. A 429, a 5xx or a failure to connect is a
 * {@link TransientHttpException} and is retried. Everything else is a
 * {@link TelemetryQueryException} and is not: a 4xx (the backend's own error text comes back so the
 * model can fix its query), a response over {@link #MAX_RESPONSE_BYTES}, and a request that ran past
 * {@code qlawkus.self-observe.timeout}. The timeout is deliberately not retried even though it is
 * network level: against a query API it means the query is too expensive, and running it four times
 * is exactly the load the timeout exists to prevent.
 */
@ApplicationScoped
public class TelemetryHttp {

  static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
  private static final int MAX_ERROR_BYTES = 8 * 1024;
  private static final int MAX_ERROR_CHARS = 500;

  private final SelfObserveConfig config;
  private final HttpClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  @Inject
  public TelemetryHttp(SelfObserveConfig config) {
    this.config = config;
    this.client = HttpClient.newBuilder()
        .connectTimeout(config.timeout())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
      jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
      retryOn = TransientHttpException.class)
  @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
  public String get(String backend, URI uri) {
    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(config.timeout())
        .header("Accept", "application/json")
        .GET()
        .build();
    HttpResponse<InputStream> response = send(backend, request);
    int status = response.statusCode();
    try (InputStream body = response.body()) {
      if (status / 100 == 2) {
        byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) {
          throw new TelemetryQueryException(backend + " answered with more than "
              + MAX_RESPONSE_BYTES / (1024 * 1024) + " MiB. Narrow the query or the window.");
        }
        return new String(bytes, StandardCharsets.UTF_8);
      }
      String detail = detail(new String(body.readNBytes(MAX_ERROR_BYTES), StandardCharsets.UTF_8));
      if (status == 429 || status >= 500) {
        throw new TransientHttpException(status, backend + ": " + detail);
      }
      throw new TelemetryQueryException(backend + " rejected the query (HTTP " + status + "): "
          + detail);
    } catch (IOException e) {
      throw new TransientHttpException(backend + " response could not be read", e);
    }
  }

  private HttpResponse<InputStream> send(String backend, HttpRequest request) {
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (HttpConnectTimeoutException e) {
      throw new TransientHttpException(backend + " did not accept a connection", e);
    } catch (HttpTimeoutException e) {
      throw new TelemetryQueryException(backend + " timed out after "
          + QueryWindow.format(config.timeout()) + " (qlawkus.self-observe.timeout). The query is "
          + "too expensive: narrow the window, the selector or the query itself.");
    } catch (IOException e) {
      throw new TransientHttpException(backend + " is unreachable: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TelemetryQueryException(backend + " request was interrupted.");
    }
  }

  private String detail(String body) {
    String text = body;
    try {
      JsonNode json = mapper.readTree(body);
      if (json != null && json.hasNonNull("error")) {
        text = json.path("error").asText();
      } else if (json != null && json.hasNonNull("message")) {
        text = json.path("message").asText();
      }
    } catch (IOException e) {
      text = body;
    }
    return Rendering.flatten(text.isBlank() ? "(no detail)" : text, MAX_ERROR_CHARS);
  }
}
