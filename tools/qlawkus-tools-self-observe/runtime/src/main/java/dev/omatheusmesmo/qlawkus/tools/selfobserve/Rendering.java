package dev.omatheusmesmo.qlawkus.tools.selfobserve;

/**
 * One backend response turned into text for the model: a {@code summary} the tool states in its
 * own voice (counts, what the caps left out) and the {@code body} that came from the backend, which
 * {@link TelemetryFrame} frames as data.
 */
public record Rendering(String summary, String body) {

  public String text() {
    return summary + "\n" + body;
  }

  static String omitted(int total, int shown, String noun) {
    if (total <= shown) {
      return "";
    }
    return " Showing " + shown + "; " + (total - shown) + " more " + noun
        + " left out by qlawkus.self-observe.max-results.";
  }

  static String flatten(String text, int maxChars) {
    String line = text.replaceAll("\\p{Cntrl}+", " ").strip();
    if (line.length() <= maxChars) {
      return line;
    }
    return line.substring(0, maxChars) + " [line truncated]";
  }
}
