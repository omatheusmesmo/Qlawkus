package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QueryWindowTest {

  private static final Duration MAX = Duration.ofHours(6);
  private static final Instant NOW = Instant.parse("2026-10-23T18:00:00Z");

  @Test
  void aLookbackWithinTheMaximumIsKept() {
    QueryWindow window = QueryWindow.resolve("1h30m", MAX, NOW);

    assertEquals(NOW.minus(Duration.ofMinutes(90)), window.start());
    assertEquals(NOW, window.end());
    assertFalse(window.clamped());
  }

  @Test
  void aLookbackBeyondTheMaximumIsClampedAndSaysSo() {
    QueryWindow window = QueryWindow.resolve("P2D", MAX, NOW);

    assertEquals(NOW.minus(MAX), window.start());
    assertTrue(window.clamped());
  }

  @Test
  void noLookbackMeansTheWholeAllowedWindow() {
    QueryWindow window = QueryWindow.resolve(null, MAX, NOW);

    assertEquals(NOW.minus(MAX), window.start());
    assertFalse(window.clamped());
  }

  @Test
  void bothPrometheusAndIsoDurationsParse() {
    assertEquals(Duration.ofMinutes(5), QueryWindow.parseDuration("5m"));
    assertEquals(Duration.ofDays(8), QueryWindow.parseDuration("1w1d"));
    assertEquals(Duration.ofMillis(1500), QueryWindow.parseDuration("1s500ms"));
    assertEquals(Duration.ofHours(2), QueryWindow.parseDuration("PT2H"));
  }

  @Test
  void anythingElseIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> QueryWindow.parseDuration("yesterday"));
    assertThrows(IllegalArgumentException.class, () -> QueryWindow.parseDuration("0m"));
    assertThrows(IllegalArgumentException.class, () -> QueryWindow.parseDuration("-5m"));
  }
}
