package dev.omatheusmesmo.qlawkus.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the external redeploy loop reports about one run once it has finished. The loop runs outside
 * the app, so this is data about something that already happened: the app validates and records it,
 * and never acts on it.
 *
 * <p>Every field is bounded. Outcomes and phase names come from closed sets, durations are capped at
 * a day, and the only free text is a short message with no control characters, so a stored report
 * can neither grow the history nor carry terminal escapes into a log or the console.
 *
 * @param startedAt when the run began, by the loop's clock
 * @param outcome {@code success} or {@code failure}
 * @param failedPhase the phase that failed; required on a failure, absent on a success
 * @param phases milliseconds each phase that ran took
 * @param message an optional short explanation, typically the loop's last error line
 */
public record RedeployReport(
    @NotNull Instant startedAt,
    @NotNull @Pattern(regexp = RedeployReport.OUTCOMES) String outcome,
    @Pattern(regexp = RedeployReport.PHASES) String failedPhase,
    @NotNull @Size(min = 1, max = 5)
    Map<@NotNull @Pattern(regexp = RedeployReport.PHASES) String,
        @NotNull @PositiveOrZero @Max(RedeployReport.MAX_PHASE_MILLIS) Long> phases,
    @Size(max = RedeployReport.MAX_MESSAGE) @Pattern(regexp = "[^\\p{Cntrl}]*") String message
) {

  public static final String SUCCESS = "success";
  public static final String FAILURE = "failure";
  public static final String OUTCOMES = SUCCESS + "|" + FAILURE;

  /** The five phases of the redeploy contract, in the order {@code redeploy.sh} runs them. */
  public static final String PHASES = "fetch|promote|build|restart|verify";
  public static final List<String> PHASE_ORDER = List.of(PHASES.split("\\|"));

  public static final long MAX_PHASE_MILLIS = 86_400_000L;
  public static final int MAX_MESSAGE = 200;

  @JsonIgnore
  @AssertTrue(message = "failedPhase is required on a failure and must be absent on a success")
  public boolean isFailedPhaseConsistent() {
    return FAILURE.equals(outcome) == (failedPhase != null);
  }
}
