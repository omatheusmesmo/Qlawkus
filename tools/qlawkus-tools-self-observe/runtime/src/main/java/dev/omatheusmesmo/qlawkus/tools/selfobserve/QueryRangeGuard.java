package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounds what a PromQL or LogQL query reads, which the request window alone does not: a range
 * selector ({@code [7d]}, or the range of a subquery {@code [1d:5m]}) or an {@code offset} reaches
 * that far back from every point it is evaluated at, whatever {@code start} and {@code end} say.
 * Each must fit in {@code qlawkus.self-observe.max-window}; one that cannot be read as a duration
 * is refused rather than trusted, and so is the {@code @} modifier, which pins evaluation to an
 * arbitrary time.
 * <p>
 * String literals are blanked before scanning, so a bracket or an {@code @} inside a label value or
 * a line filter is not mistaken for syntax.
 */
public final class QueryRangeGuard {

  private static final Pattern RANGE = Pattern.compile("\\[([^\\]]*)]");
  private static final Pattern OFFSET = Pattern.compile("(?i)\\boffset\\s+-?\\s*([0-9a-z]+)");

  private QueryRangeGuard() {
  }

  public static Optional<String> check(String query, Duration max) {
    String code = blankStrings(query);
    if (code.indexOf('@') >= 0) {
      return Optional.of("The query uses the @ modifier, which evaluates outside the allowed "
          + "window (qlawkus.self-observe.max-window = " + QueryWindow.format(max)
          + "). Remove it and use a lookback instead.");
    }
    Matcher range = RANGE.matcher(code);
    while (range.find()) {
      String inner = range.group(1);
      int colon = inner.indexOf(':');
      Optional<String> refusal = verify(colon >= 0 ? inner.substring(0, colon) : inner,
          "range [" + inner + "]", max);
      if (refusal.isPresent()) {
        return refusal;
      }
    }
    Matcher offset = OFFSET.matcher(code);
    while (offset.find()) {
      Optional<String> refusal = verify(offset.group(1), "offset " + offset.group(1), max);
      if (refusal.isPresent()) {
        return refusal;
      }
    }
    return Optional.empty();
  }

  private static Optional<String> verify(String duration, String what, Duration max) {
    try {
      if (QueryWindow.parseDuration(duration).compareTo(max) <= 0) {
        return Optional.empty();
      }
      return Optional.of("The " + what + " reaches further back than qlawkus.self-observe."
          + "max-window (" + QueryWindow.format(max) + "). Use a shorter one.");
    } catch (IllegalArgumentException e) {
      return Optional.of("Could not check the " + what + " against qlawkus.self-observe.max-window ("
          + QueryWindow.format(max) + "). Write it as a plain duration such as 5m or 1h.");
    }
  }

  private static String blankStrings(String query) {
    StringBuilder out = new StringBuilder(query.length());
    int i = 0;
    while (i < query.length()) {
      char c = query.charAt(i);
      if (c == '"' || c == '\'' || c == '`') {
        int j = i + 1;
        while (j < query.length() && query.charAt(j) != c) {
          j += (c != '`' && query.charAt(j) == '\\') ? 2 : 1;
        }
        out.append(c).append(c);
        i = j + 1;
      } else {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }
}
