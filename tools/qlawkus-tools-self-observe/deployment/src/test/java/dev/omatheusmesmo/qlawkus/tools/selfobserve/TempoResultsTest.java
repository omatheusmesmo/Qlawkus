package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** The trace summary: span tree order, the span cap, and the response shapes Tempo serves. */
class TempoResultsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String SPANS = """
      [
        {"spanId":"c","parentSpanId":"a","name":"second-child","startTimeUnixNano":"300","endTimeUnixNano":"400"},
        {"spanId":"b","parentSpanId":"a","name":"first-child","startTimeUnixNano":"200","endTimeUnixNano":"250"},
        {"spanId":"d","parentSpanId":"b","name":"grandchild","startTimeUnixNano":"210","endTimeUnixNano":"220"},
        {"spanId":"a","name":"root","startTimeUnixNano":"100","endTimeUnixNano":"900"}
      ]""";

  @Test
  void spansAreListedDepthFirstInStartOrderAndIndentedByDepth() throws Exception {
    String summary = TempoResults.summarizeTrace("abc", batches(SPANS), 50).text();

    int root = summary.indexOf("- root");
    int first = summary.indexOf("  - first-child");
    int grandchild = summary.indexOf("    - grandchild");
    int second = summary.indexOf("  - second-child");
    assertTrue(root >= 0 && root < first && first < grandchild && grandchild < second, summary);
  }

  @Test
  void spansBeyondTheCapAreCountedNotListed() throws Exception {
    String summary = TempoResults.summarizeTrace("abc", batches(SPANS), 2).text();

    assertTrue(summary.contains("first-child"), summary);
    assertFalse(summary.contains("grandchild"), summary);
    assertFalse(summary.contains("second-child"), summary);
    assertTrue(summary.contains("2 more spans"), summary);
  }

  @Test
  void theResourceSpansShapeIsReadToo() throws Exception {
    JsonNode root = MAPPER.readTree("""
        {"trace":{"resourceSpans":[{"scopeSpans":[{"spans":%s}]}]}}""".formatted(SPANS));

    assertTrue(TempoResults.summarizeTrace("abc", root, 50).text().contains("grandchild"));
  }

  @Test
  void numericErrorStatusIsReported() throws Exception {
    JsonNode root = batches("""
        [{"spanId":"a","name":"root","startTimeUnixNano":"0","endTimeUnixNano":"1000000",
          "status":{"code":2,"message":"boom"}}]""");

    assertTrue(TempoResults.summarizeTrace("abc", root, 50).text().contains("ERROR: boom"));
  }

  private static JsonNode batches(String spans) throws Exception {
    return MAPPER.readTree("""
        {"batches":[{"scopeSpans":[{"spans":%s}]}]}""".formatted(spans));
  }
}
