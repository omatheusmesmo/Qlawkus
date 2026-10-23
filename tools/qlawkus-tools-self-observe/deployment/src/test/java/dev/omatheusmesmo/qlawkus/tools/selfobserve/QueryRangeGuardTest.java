package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The request window alone does not bound a PromQL or LogQL query: a range selector or an
 * {@code offset} inside the query reads further back than the window it is evaluated in.
 */
class QueryRangeGuardTest {

  private static final Duration MAX = Duration.ofHours(6);

  @ParameterizedTest
  @ValueSource(strings = {
      "rate(qlawkus_tool_invocations_total[5m])",
      "sum_over_time(up[6h])",
      "max_over_time(rate(x[5m])[1h:1m])",
      "count_over_time({service_name=\"qlawkus-app\"} |= \"error\" [1h30m])",
      "up{instance=~\"[0-9]+:9000\"}",
      "{job=\"a\"} |~ `[30d]`",
      "x offset 1h",
      "qlawkus_model_circuit_state"})
  void queriesWithinTheWindowPass(String query) {
    assertEquals(Optional.empty(), QueryRangeGuard.check(query, MAX));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "rate(qlawkus_tool_invocations_total[7d])",
      "sum_over_time(up[6h1m])",
      "max_over_time(rate(x[5m])[1d:5m])",
      "count_over_time({service_name=\"qlawkus-app\"}[2w])",
      "x offset 30d",
      "x OFFSET -1y",
      "rate(x[1y])"})
  void queriesReachingBeyondTheWindowAreRefused(String query) {
    Optional<String> refusal = QueryRangeGuard.check(query, MAX);

    assertTrue(refusal.isPresent(), query);
    assertTrue(refusal.get().contains("max-window"), refusal.get());
  }

  @ParameterizedTest
  @ValueSource(strings = {"rate(x[$__interval])", "x @ 1609746000", "x @ start()"})
  void rangesThatCannotBeVerifiedAreRefused(String query) {
    assertTrue(QueryRangeGuard.check(query, MAX).isPresent(), query);
  }
}
