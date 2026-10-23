package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders Tempo search results and single traces. A trace is summarized, never passed through: the
 * raw OTLP JSON is mostly ids, timestamps and encoding, and its attributes can hold what the owner
 * said (tool arguments and results, when tracing is configured to record them). Each span becomes
 * one line, depth-first in start order and indented by depth, with its duration, its error status
 * and only the attributes in {@link #ATTRIBUTES}.
 */
final class TempoResults {

  /** The span attributes a summary may show: what a span did, never what it was given. */
  static final Set<String> ATTRIBUTES = Set.of(
      "gen_ai.operation.name", "gen_ai.tool.name", "gen_ai.system", "gen_ai.provider.name",
      "gen_ai.request.model", "gen_ai.response.model",
      "gen_ai.usage.input_tokens", "gen_ai.usage.output_tokens",
      "http.request.method", "http.response.status_code", "http.route", "server.address",
      "db.system", "db.system.name", "db.operation.name", "db.collection.name",
      "error.type", "exception.type");

  private static final int MAX_MATCHED_SPANS = 3;
  private static final int MAX_VALUE_CHARS = 200;

  private TempoResults() {
  }

  static Rendering renderSearch(JsonNode root, int maxResults) {
    JsonNode traces = root.path("traces");
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < traces.size() && i < maxResults; i++) {
      JsonNode trace = traces.get(i);
      StringBuilder line = new StringBuilder("- ").append(trace.path("traceID").asText())
          .append("  \"").append(trace.path("rootTraceName").asText("?")).append("\"")
          .append(" (service ").append(trace.path("rootServiceName").asText("?")).append(")")
          .append("  started ").append(instant(trace.path("startTimeUnixNano").asText("0")))
          .append("  ").append(trace.has("durationMs") ? trace.path("durationMs").asText() : "<1")
          .append(" ms");
      int matched = 0;
      for (JsonNode spanSet : spanSets(trace)) {
        for (JsonNode span : spanSet.path("spans")) {
          if (matched++ < MAX_MATCHED_SPANS) {
            line.append("\n    matched ").append(span.path("name").asText("?")).append("  ")
                .append(millis(span.path("durationNanos").asLong()));
          }
        }
      }
      lines.add(line.toString());
    }
    String summary = "Tempo: " + traces.size() + " traces."
        + Rendering.omitted(traces.size(), lines.size(), "traces");
    return new Rendering(summary, lines.isEmpty() ? "(no traces)" : String.join("\n", lines));
  }

  static Rendering summarizeTrace(String traceId, JsonNode root, int maxSpans) {
    List<Span> spans = spans(root);
    Map<String, Span> byId = new HashMap<>();
    spans.forEach(span -> byId.put(span.id(), span));
    Map<String, List<Span>> children = new HashMap<>();
    List<Span> roots = new ArrayList<>();
    for (Span span : spans) {
      if (span.parentId().isEmpty() || !byId.containsKey(span.parentId())) {
        roots.add(span);
      } else {
        children.computeIfAbsent(span.parentId(), k -> new ArrayList<>()).add(span);
      }
    }
    List<String> lines = new ArrayList<>();
    roots.stream().sorted(Comparator.comparingLong(Span::start))
        .forEach(span -> walk(span, 0, null, children, lines, maxSpans));
    long errors = spans.stream().filter(Span::error).count();
    String summary = "Tempo: trace " + traceId + ", " + spans.size() + " spans, " + errors
        + " with errors." + Rendering.omitted(spans.size(), lines.size(), "spans");
    return new Rendering(summary, lines.isEmpty() ? "(no spans)" : String.join("\n", lines));
  }

  private static void walk(Span span, int depth, String parentService,
      Map<String, List<Span>> children, List<String> lines, int maxSpans) {
    if (lines.size() >= maxSpans) {
      return;
    }
    StringBuilder line = new StringBuilder("  ".repeat(depth)).append("- ").append(span.name())
        .append("  ").append(millis(span.end() - span.start()));
    if (span.error()) {
      line.append("  ERROR");
      if (!span.statusMessage().isBlank()) {
        line.append(": ").append(span.statusMessage());
      }
    }
    List<String> attributes = new ArrayList<>();
    if (!span.service().isEmpty() && !span.service().equals(parentService)) {
      attributes.add("service=" + span.service());
    }
    attributes.addAll(span.attributes());
    if (!attributes.isEmpty()) {
      line.append("  [").append(String.join(" ", attributes)).append("]");
    }
    lines.add(line.toString());
    children.getOrDefault(span.id(), List.of()).stream()
        .sorted(Comparator.comparingLong(Span::start))
        .forEach(child -> walk(child, depth + 1, span.service(), children, lines, maxSpans));
  }

  private static List<Span> spans(JsonNode root) {
    JsonNode resources = root.path("trace").path("resourceSpans");
    if (resources.isMissingNode()) {
      resources = root.has("batches") ? root.path("batches") : root.path("resourceSpans");
    }
    List<Span> spans = new ArrayList<>();
    for (JsonNode resource : resources) {
      String service = attribute(resource.path("resource").path("attributes"), "service.name");
      JsonNode scopes = resource.has("scopeSpans") ? resource.path("scopeSpans")
          : resource.path("instrumentationLibrarySpans");
      for (JsonNode scope : scopes) {
        for (JsonNode span : scope.path("spans")) {
          spans.add(span(span, service));
        }
      }
    }
    return spans;
  }

  private static Span span(JsonNode span, String service) {
    JsonNode status = span.path("status");
    String code = status.path("code").asText("");
    List<String> attributes = new ArrayList<>();
    for (JsonNode attribute : span.path("attributes")) {
      String key = attribute.path("key").asText();
      if (ATTRIBUTES.contains(key)) {
        attributes.add(key + "=" + Rendering.flatten(value(attribute.path("value")), MAX_VALUE_CHARS));
      }
    }
    return new Span(span.path("spanId").asText(""), span.path("parentSpanId").asText(""),
        Rendering.flatten(span.path("name").asText("?"), MAX_VALUE_CHARS),
        span.path("startTimeUnixNano").asLong(), span.path("endTimeUnixNano").asLong(),
        code.equals("STATUS_CODE_ERROR") || code.equals("2"),
        Rendering.flatten(status.path("message").asText(""), MAX_VALUE_CHARS), service, attributes);
  }

  private static List<JsonNode> spanSets(JsonNode trace) {
    Set<JsonNode> sets = new LinkedHashSet<>();
    if (trace.has("spanSet")) {
      sets.add(trace.path("spanSet"));
    }
    trace.path("spanSets").forEach(sets::add);
    return new ArrayList<>(sets);
  }

  private static String attribute(JsonNode attributes, String key) {
    for (JsonNode attribute : attributes) {
      if (attribute.path("key").asText().equals(key)) {
        return value(attribute.path("value"));
      }
    }
    return "";
  }

  private static String value(JsonNode value) {
    for (String field : List.of("stringValue", "intValue", "doubleValue", "boolValue")) {
      if (value.has(field)) {
        return value.path(field).asText();
      }
    }
    return "";
  }

  private static String millis(long nanos) {
    return String.format(Locale.ROOT, "%.1f ms", nanos / 1_000_000.0);
  }

  private static String instant(String unixNanos) {
    try {
      long nanos = Long.parseLong(unixNanos);
      return Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L).toString();
    } catch (NumberFormatException e) {
      return unixNanos;
    }
  }

  private record Span(String id, String parentId, String name, long start, long end,
      boolean error, String statusMessage, String service, List<String> attributes) {
  }
}
