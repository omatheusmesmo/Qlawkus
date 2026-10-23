package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders a Loki {@code query_range} response. Log streams are merged into one list, newest first,
 * one entry per line: control characters (newlines included) are flattened so a log line cannot
 * pose as a line of the tool's own output, and each line is cut to {@link #MAX_LINE_CHARS} so a
 * single entry cannot take the whole output budget. A metric query (a matrix) renders like
 * Prometheus.
 */
final class LokiResults {

  static final int MAX_LINE_CHARS = 1000;
  private static final List<String> LEVEL_LABELS = List.of("level", "detected_level",
      "severity_text");

  private LokiResults() {
  }

  static Rendering render(JsonNode root, int maxResults) {
    JsonNode data = root.path("data");
    if (!data.path("resultType").asText().equals("streams")) {
      return PrometheusResults.render("Loki", root, maxResults);
    }
    List<Entry> entries = new ArrayList<>();
    for (JsonNode stream : data.path("result")) {
      String level = level(stream.path("stream"));
      for (JsonNode value : stream.path("values")) {
        entries.add(new Entry(value.path(0).asLong(), level, value.path(1).asText("")));
      }
    }
    entries.sort(Comparator.comparingLong(Entry::nanos).reversed());
    List<String> lines = entries.stream().limit(maxResults)
        .map(entry -> instant(entry.nanos())
            + (entry.level().isEmpty() ? "" : " [" + entry.level() + "]")
            + " " + Rendering.flatten(entry.line(), MAX_LINE_CHARS))
        .toList();
    String summary = "Loki: " + entries.size() + " lines, newest first."
        + Rendering.omitted(entries.size(), lines.size(), "lines");
    return new Rendering(summary, lines.isEmpty() ? "(no lines)" : String.join("\n", lines));
  }

  private static String level(JsonNode labels) {
    for (String label : LEVEL_LABELS) {
      String value = labels.path(label).asText("");
      if (!value.isBlank()) {
        return Rendering.flatten(value, 20);
      }
    }
    return "";
  }

  private static String instant(long nanos) {
    return Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L).toString();
  }

  private record Entry(long nanos, String level, String line) {
  }
}
