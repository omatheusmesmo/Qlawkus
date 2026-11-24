package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RestClient;

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
  private final TelemetryQueryClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  @Inject
  public TelemetryHttp(SelfObserveConfig config, @RestClient TelemetryQueryClient client) {
    this.config = config;
    this.client = client;
  }

  @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
      jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
      retryOn = TransientHttpException.class)
  @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
  public String get(String backend, URI uri) {
    Response response;
    try {
      response = client.get(uri.toString());
    } catch (WebApplicationException e) {
      response = e.getResponse();
    } catch (ProcessingException e) {
      if (isReadTimeout(e)) {
        throw new TelemetryQueryException(backend + " timed out after "
            + QueryWindow.format(config.timeout()) + " (qlawkus.self-observe.timeout). The query is "
            + "too expensive: narrow the window, the selector or the query itself.");
      }
      throw new TransientHttpException(backend + " is unreachable: " + e.getMessage(), e);
    }
    int status = response.getStatus();
    try (InputStream body = response.readEntity(InputStream.class)) {
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
    } finally {
      response.close();
    }
  }

  private static boolean isReadTimeout(Throwable e) {
    while (e != null) {
      if (e instanceof SocketTimeoutException) {
        return true;
      }
      if (e.getMessage() != null && e.getMessage().contains("timeout period")) {
        return true;
      }
      e = e.getCause();
    }
    return false;
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
