package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders the Prometheus query response shape, which Loki also uses for metric queries. An instant
 * vector is one line per series with its value; a range matrix is one line per series with its point
 * count and minimum, maximum and last value, since the individual points would cost far more of the
 * output budget than they tell.
 */
final class PrometheusResults {

  private PrometheusResults() {
  }

  static Rendering render(String backend, JsonNode root, int maxResults) {
    JsonNode data = root.path("data");
    String type = data.path("resultType").asText("");
    JsonNode result = data.path("result");
    if (type.equals("scalar") || type.equals("string")) {
      return new Rendering(backend + ": a " + type + ".", type + " " + result.path(1).asText());
    }
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < result.size() && i < maxResults; i++) {
      JsonNode series = result.get(i);
      String labels = labels(series.path("metric"));
      lines.add(type.equals("matrix") ? labels + "  " + summarize(series.path("values"))
          : labels + "  " + series.path("value").path(1).asText());
    }
    String summary = backend + ": " + result.size() + " series."
        + Rendering.omitted(result.size(), lines.size(), "series");
    return new Rendering(summary, lines.isEmpty() ? "(no series)" : String.join("\n", lines));
  }

  private static String labels(JsonNode metric) {
    String name = metric.path("__name__").asText("");
    List<String> pairs = new ArrayList<>();
    for (Map.Entry<String, JsonNode> label : metric.properties()) {
      if (!label.getKey().equals("__name__")) {
        pairs.add(label.getKey() + "=\"" + label.getValue().asText() + "\"");
      }
    }
    return name + "{" + String.join(",", pairs) + "}";
  }

  private static String summarize(JsonNode values) {
    if (values.isEmpty()) {
      return "points=0";
    }
    String min = null;
    String max = null;
    for (JsonNode point : values) {
      String value = point.path(1).asText();
      if (min == null || number(value) < number(min)) {
        min = value;
      }
      if (max == null || number(value) > number(max)) {
        max = value;
      }
    }
    String last = values.get(values.size() - 1).path(1).asText();
    return "points=" + values.size() + " min=" + min + " max=" + max + " last=" + last;
  }

  private static double number(String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
  }
}
