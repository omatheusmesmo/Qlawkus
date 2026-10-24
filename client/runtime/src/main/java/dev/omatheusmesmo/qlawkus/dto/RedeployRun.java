package dev.omatheusmesmo.qlawkus.dto;

import java.util.Map;

/**
 * One recorded redeploy run, as the history keeps and serves it.
 *
 * @param recordedAt ISO-8601 instant the app received the report, by the app's own clock
 * @param startedAt ISO-8601 instant the run began, by the redeploy loop's clock
 * @param outcome {@code success} or {@code failure}
 * @param failedPhase the phase that failed, or {@code null} on a success
 * @param phases milliseconds per phase that ran, in contract order
 * @param message the loop's short explanation, or {@code null}
 */
public record RedeployRun(
    String recordedAt,
    String startedAt,
    String outcome,
    String failedPhase,
    Map<String, Long> phases,
    String message
) {}
