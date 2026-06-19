package dev.omatheusmesmo.qlawkus.tool.review;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tool.shell.ShellTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
        CommandResult result = shellTool.runCommand(command, null, TIMEOUT_SECONDS);
        Log.infof("REVIEW_RESPONSE | pr=%d type=%s exit=%d duration=%dms", prNumber, type, result.exitCode(), result.durationMs());
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
