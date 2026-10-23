package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The time range one query covers: the look-back the model asked for, ending now, clamped to
 * {@code qlawkus.self-observe.max-window}. No look-back means the whole allowed window.
 * <p>
 * Durations are read in the Prometheus form the model already writes in queries ({@code 30m},
 * {@code 1h30m}, {@code 2d}, where {@code w} is 7 days and {@code y} 365) or as ISO-8601
 * ({@code PT2H}). Zero and negative durations are rejected.
 */
public record QueryWindow(Instant start, Instant end, boolean clamped) {

  private static final Pattern PROMETHEUS = Pattern.compile("(\\d+(ms|[smhdwy]))+");
  private static final Pattern PART = Pattern.compile("(\\d+)(ms|[smhdwy])");

  public static QueryWindow resolve(String lookback, Duration max, Instant now) {
    if (lookback == null || lookback.isBlank()) {
      return new QueryWindow(now.minus(max), now, false);
    }
    Duration requested = parseDuration(lookback);
    boolean clamped = requested.compareTo(max) > 0;
    return new QueryWindow(now.minus(clamped ? max : requested), now, clamped);
  }

  public Duration length() {
    return Duration.between(start, end);
  }

  public static Duration parseDuration(String text) {
    String value = text == null ? "" : text.strip();
    Duration duration;
    if (PROMETHEUS.matcher(value).matches()) {
      duration = Duration.ZERO;
      Matcher part = PART.matcher(value);
      while (part.find()) {
        duration = duration.plus(unit(Long.parseLong(part.group(1)), part.group(2)));
      }
    } else if (value.toUpperCase(Locale.ROOT).startsWith("P")) {
      try {
        duration = Duration.parse(value.toUpperCase(Locale.ROOT));
      } catch (DateTimeParseException e) {
        throw new IllegalArgumentException("Not a duration: " + text, e);
      }
    } else {
      throw new IllegalArgumentException("Not a duration: " + text);
    }
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException("A duration must be positive: " + text);
    }
    return duration;
  }

  public static String format(Duration duration) {
    long seconds = duration.toSeconds();
    if (duration.toMillisPart() != 0) {
      return duration.toMillis() + "ms";
    }
    if (seconds % 86_400 == 0) {
      return seconds / 86_400 + "d";
    }
    if (seconds % 3_600 == 0) {
      return seconds / 3_600 + "h";
    }
    if (seconds % 60 == 0) {
      return seconds / 60 + "m";
    }
    return seconds + "s";
  }

  private static Duration unit(long amount, String unit) {
    return switch (unit) {
      case "ms" -> Duration.ofMillis(amount);
      case "s" -> Duration.ofSeconds(amount);
      case "m" -> Duration.ofMinutes(amount);
      case "h" -> Duration.ofHours(amount);
      case "d" -> Duration.ofDays(amount);
      case "w" -> Duration.ofDays(7 * amount);
      default -> Duration.ofDays(365 * amount);
    };
  }
}
