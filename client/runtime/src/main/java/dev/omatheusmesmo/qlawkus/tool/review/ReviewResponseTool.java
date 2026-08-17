package dev.omatheusmesmo.qlawkus.tool.review;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tool.shell.ShellTool;
import io.quarkus.logging.Log;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/**
 * Submits a code-review response on a GitHub PR using the {@code gh pr review} CLI.
 *
 * <p>Supports three review types:
 * <ul>
 *   <li>{@code APPROVE} — approve the PR</li>
 *   <li>{@code REQUEST_CHANGES} — request changes (body required)</li>
 *   <li>{@code COMMENT} — leave a comment without approving or blocking</li>
 * </ul>
 */
@QlawTool
@ApplicationScoped
public class ReviewResponseTool {

    static final int TIMEOUT_SECONDS = 30;

    /**
     * {@code gh} has no structured way to tell a transient failure (GitHub's API is down or
     * rate-limited) from a permanent one (bad PR number, no permissions) - the CLI just exits 1
     * either way. This is a best-effort text match against {@code gh}'s own error message shapes for
     * a 429/5xx or a network-level failure reaching the API at all; anything else is treated as
     * permanent and surfaced immediately.
     */
    private static final Pattern TRANSIENT_FAILURE = Pattern.compile(
            "HTTP 429|HTTP 5\\d\\d|connection reset|context deadline exceeded|dial tcp|i/o timeout",
            Pattern.CASE_INSENSITIVE);

    @Inject
    @QlawTool
    ShellTool shellTool;

    @Tool("""
            Submit a review on a GitHub Pull Request using 'gh pr review'.
            Parameters:
              prNumber (required) — the PR number to review, e.g. 42.
              reviewType (required) — one of: APPROVE, REQUEST_CHANGES, COMMENT.
              body (required for REQUEST_CHANGES, optional otherwise) — the review message.
            Returns the gh CLI output and exit code.
            """)
    public CommandResult submitReview(
            @P("Pull request number") int prNumber,
            @P("Review type: APPROVE, REQUEST_CHANGES, or COMMENT") String reviewType,
            @P(value = "Review body / comment text. Required when reviewType is REQUEST_CHANGES.", required = false)
            String body) {

        ReviewType type;
        try {
            type = ReviewType.valueOf(reviewType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return reject("Unknown review type '" + reviewType + "'. Must be one of: APPROVE, REQUEST_CHANGES, COMMENT.");
        }

        if (type == ReviewType.REQUEST_CHANGES && (body == null || body.isBlank())) {
            return reject("A review body is required when reviewType is REQUEST_CHANGES.");
        }

        String command = buildCommand(prNumber, type, body);
        Log.infof("REVIEW_RESPONSE | pr=%d type=%s", prNumber, type);
        CommandResult result;
        try {
            result = runReviewCommand(command);
        } catch (TransientHttpException e) {
            Log.warnf("REVIEW_RESPONSE | pr=%d type=%s gave up after retries: %s", prNumber, type, e.getMessage());
            return reject("gh pr review kept failing transiently: " + e.getMessage());
        }
        Log.infof("REVIEW_RESPONSE | pr=%d type=%s exit=%d duration=%dms", prNumber, type, result.exitCode(), result.durationMs());
        return result;
    }

    /**
     * {@code @Retry} covers one {@code gh pr review} invocation, not one {@link #submitReview} call.
     * A permanent failure (bad PR number, no permissions, ...) returns its {@link CommandResult}
     * as-is on the first try; only a failure that {@link #TRANSIENT_FAILURE} recognizes as transient
     * is turned into an exception so {@code @Retry} sees it and tries again.
     */
    @Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
            jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
            retryOn = TransientHttpException.class)
    @ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
    CommandResult runReviewCommand(String command) {
        CommandResult result = shellTool.runCommand(command, null, TIMEOUT_SECONDS);
        if (result.exitCode() != 0 && result.stderr() != null && TRANSIENT_FAILURE.matcher(result.stderr()).find()) {
            throw new TransientHttpException("gh pr review failed transiently: " + result.stderr());
        }
        return result;
    }

    private String buildCommand(int prNumber, ReviewType type, String body) {
        StringBuilder cmd = new StringBuilder("gh pr review ").append(prNumber);
        switch (type) {
            case APPROVE -> cmd.append(" --approve");
            case REQUEST_CHANGES -> cmd.append(" --request-changes");
            case COMMENT -> cmd.append(" --comment");
        }
        if (body != null && !body.isBlank()) {
            cmd.append(" --body ").append(shellQuote(body));
        }
        return cmd.toString();
    }

    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private CommandResult reject(String reason) {
        Log.warnf("REVIEW_RESPONSE | rejected — %s", reason);
        return new CommandResult("", reason, -10, 0L, false);
    }

    enum ReviewType {
        APPROVE, REQUEST_CHANGES, COMMENT
    }
}
