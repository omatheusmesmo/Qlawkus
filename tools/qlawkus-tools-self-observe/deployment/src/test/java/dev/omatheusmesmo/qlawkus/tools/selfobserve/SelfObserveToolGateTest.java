package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The refusals that must happen before any request: the {@code enabled} switch, a backend whose URL
 * is unset, and a backend URL that is not HTTP.
 */
class SelfObserveToolGateTest {

  @Test
  void disabledRefusesEveryToolAndSendsNothing() {
    RecordingHttp http = new RecordingHttp();
    SelfObserveTool tool = new SelfObserveTool(
        new TestConfig(false, "http://prom", "http://tempo", "http://loki"), http);

    List<String> results = List.of(
        tool.queryMetrics("up", null),
        tool.findTraces("{ status = error }", null),
        tool.getTrace("0af7651916cd43dd8448eb211c80319c"),
        tool.queryLogs("{service_name=\"qlawkus-app\"}", null));

    results.forEach(r -> assertTrue(r.contains("qlawkus.self-observe.enabled=false"), r));
    assertEquals(List.of(), http.calls);
  }

  @Test
  void anUnsetBackendIsNamedInsteadOfFailing() {
    RecordingHttp http = new RecordingHttp();
    SelfObserveTool tool = new SelfObserveTool(new TestConfig(true, null, null, null), http);

    assertTrue(tool.queryMetrics("up", null).contains("qlawkus.self-observe.prometheus-url"));
    assertTrue(tool.findTraces("{ status = error }", null).contains("qlawkus.self-observe.tempo-url"));
    assertTrue(tool.getTrace("0af7651916cd43dd8448eb211c80319c")
        .contains("qlawkus.self-observe.tempo-url"));
    assertTrue(tool.queryLogs("{service_name=\"qlawkus-app\"}", null)
        .contains("qlawkus.self-observe.loki-url"));
    assertEquals(List.of(), http.calls);
  }

  @Test
  void aNonHttpBackendUrlIsRefused() {
    RecordingHttp http = new RecordingHttp();
    SelfObserveTool tool = new SelfObserveTool(
        new TestConfig(true, "file:///etc", null, null), http);

    assertTrue(tool.queryMetrics("up", null).contains("prometheus-url"));
    assertEquals(List.of(), http.calls);
  }

  @Test
  void aBlankQueryIsRefused() {
    RecordingHttp http = new RecordingHttp();
    SelfObserveTool tool = new SelfObserveTool(
        new TestConfig(true, "http://prom", "http://tempo", "http://loki"), http);

    assertTrue(tool.queryMetrics("  ", null).contains("PromQL"));
    assertTrue(tool.queryLogs(null, null).contains("LogQL"));
    assertEquals(List.of(), http.calls);
  }

  @Test
  void anUnparseableLookbackIsRefused() {
    RecordingHttp http = new RecordingHttp();
    SelfObserveTool tool = new SelfObserveTool(
        new TestConfig(true, "http://prom", "http://tempo", "http://loki"), http);

    assertTrue(tool.queryLogs("{a=\"b\"}", "yesterday").contains("lookback"));
    assertEquals(List.of(), http.calls);
  }

  private static final class RecordingHttp extends TelemetryHttp {
    final List<URI> calls = new ArrayList<>();

    RecordingHttp() {
      super(new TestConfig(true, null, null, null));
    }

    @Override
    public String get(String backend, URI uri) {
      calls.add(uri);
      return "{}";
    }
  }

  private record TestConfig(boolean enabled, String prometheus, String tempo, String loki)
      implements SelfObserveConfig {

    @Override
    public Optional<String> prometheusUrl() {
      return Optional.ofNullable(prometheus);
    }

    @Override
    public Optional<String> tempoUrl() {
      return Optional.ofNullable(tempo);
    }

    @Override
    public Optional<String> lokiUrl() {
      return Optional.ofNullable(loki);
    }

    @Override
    public Duration maxWindow() {
      return Duration.ofHours(6);
    }

    @Override
    public int maxResults() {
      return 50;
    }

    @Override
    public int maxChars() {
      return 8000;
    }

    @Override
    public Duration timeout() {
      return Duration.ofSeconds(10);
    }
  }
}
