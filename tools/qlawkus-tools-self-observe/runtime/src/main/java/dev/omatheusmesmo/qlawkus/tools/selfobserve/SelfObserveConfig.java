package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Optional;

/**
 * Runtime configuration for the self-observation tools, which let the agent query its own metrics,
 * traces and logs. Owned by the optional {@code qlawkus-self-observe} extension; absent from a
 * distribution that does not ship it.
 * <p>
 * The backend URLs come only from here: the model supplies a query string, never a host or a path.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.self-observe")
public interface SelfObserveConfig {

  /**
   * Whether the self-observation tools ({@code queryMetrics}, {@code findTraces}, {@code getTrace},
   * {@code queryLogs}) are active. When {@code false}, every tool refuses and nothing reaches the
   * network.
   */
  @WithDefault("false")
  boolean enabled();

  /**
   * Base URL of the Prometheus HTTP API (for example {@code http://lgtm:9090}). {@code queryMetrics}
   * calls {@code /api/v1/query} and {@code /api/v1/query_range} under it. When unset, the tool says
   * metrics are not configured instead of failing.
   */
  Optional<String> prometheusUrl();

  /**
   * Base URL of the Tempo HTTP API (for example {@code http://lgtm:3200}). {@code findTraces} calls
   * {@code /api/search} and {@code getTrace} calls {@code /api/traces/<id>} under it. When unset, the
   * trace tools say traces are not configured instead of failing.
   */
  Optional<String> tempoUrl();

  /**
   * Base URL of the Loki HTTP API (for example {@code http://lgtm:3100}). {@code queryLogs} calls
   * {@code /loki/api/v1/query_range} under it. When unset, the tool says logs are not configured
   * instead of failing.
   */
  Optional<String> lokiUrl();

  /**
   * The largest look-back any query may use. A requested look-back above it is clamped to it, and a
   * PromQL or LogQL range selector or {@code offset} longer than it is refused before any request.
   */
  @WithDefault("6h")
  Duration maxWindow();

  /**
   * The most series, traces, spans or log lines one call returns. Anything beyond it is dropped and
   * the result says how much was left out.
   */
  @WithDefault("50")
  int maxResults();

  /**
   * The most characters one call returns, framing included. Longer output is cut and marked as
   * truncated.
   */
  @WithDefault("8000")
  int maxChars();

  /**
   * Timeout for one HTTP request to a backend. A query that exceeds it is reported to the model as
   * too expensive and is not retried.
   */
  @WithDefault("10s")
  Duration timeout();
}
