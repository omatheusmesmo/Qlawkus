package dev.omatheusmesmo.qlawkus.tools.selfobserve;

/**
 * A telemetry query that failed for a reason retrying cannot fix: the backend rejected it (a 4xx,
 * typically a syntax error), it ran past the timeout, or its answer was too large. The message is
 * written for the model, so it can correct the query and call again.
 */
public class TelemetryQueryException extends RuntimeException {

  public TelemetryQueryException(String message) {
    super(message);
  }
}
