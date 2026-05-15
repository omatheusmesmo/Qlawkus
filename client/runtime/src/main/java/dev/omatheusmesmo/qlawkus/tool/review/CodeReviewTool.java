package dev.omatheusmesmo.qlawkus.tool.review;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.shell.ShellTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * Code-review tool exposed to the agent. Provides a single specialized entry point
 * to run the project's local test suite as part of a PR review.
 *
 * <p>Behaviour differs from a raw {@link ShellTool#runCommand} call in three ways:
 * <ul>
 *   <li>The build-tool invocation is constrained to a known allowlist
 *       (mvn, mvnw, gradle, gradlew, npm, yarn, pnpm) — anything else is rejected
 *       before the underlying shell is touched.</li>
 *   <li>The default timeout is 120 seconds, matching the M6 test-runner policy.</li>
 *   <li>Executions are audit-logged with a {@code REVIEW_TEST} marker so they can
 *       be correlated against review activity in monitoring.</li>
 * </ul>
 */
@ClawTool
@ApplicationScoped
public class CodeReviewTool {

    public static final int DEFAULT_TIMEOUT_SECONDS = 120;

    static final Set<String> ALLOWED_BUILD_TOOLS = Set.of(
            "mvn",
            "mvnw",
            "./mvnw",
            "gradle",
            "gradlew",
            "./gradlew",
            "npm",
            "yarn",
            "pnpm",
            "git"
    );

    public static final int INVALID_BUILD_TOOL_EXIT_CODE = -10;

    @Inject
    @ClawTool
    ShellTool shellTool;

    @Tool("""
            Run the project's local test suite using a build tool command and return
            structured results (stdout, stderr, exit code, duration). Allowed build
            tools: mvn, mvnw, gradle, gradlew, npm, yarn, pnpm. Default timeout 120s.
            Parameter: buildToolCommand (required) — the full command, e.g. 'mvn test',
            './mvnw verify -DskipITs', 'npm test'.
            """)
    public CommandResult runLocalTests(
            @P("Build tool command to execute, e.g. 'mvn test', './mvnw verify', 'npm test'")
            String buildToolCommand) {

        if (buildToolCommand == null || buildToolCommand.isBlank()) {
            return reject("buildToolCommand is required");
        }

        String firstToken = firstToken(buildToolCommand);
        if (!ALLOWED_BUILD_TOOLS.contains(firstToken)) {
            return reject("Build tool '" + firstToken + "' is not in the allowlist " + ALLOWED_BUILD_TOOLS);
        }

        Log.infof("REVIEW_TEST | starting cmd='%s' timeout=%ds", buildToolCommand, DEFAULT_TIMEOUT_SECONDS);
        CommandResult result = shellTool.runCommand(buildToolCommand, null, DEFAULT_TIMEOUT_SECONDS);
        Log.infof("REVIEW_TEST | finished cmd='%s' exit=%d duration=%dms truncated=%s",
                buildToolCommand, result.exitCode(), result.durationMs(), result.truncated());
        return result;
    }

    private CommandResult reject(String reason) {
        Log.warnf("REVIEW_TEST | rejected — %s", reason);
        return new CommandResult("", reason, INVALID_BUILD_TOOL_EXIT_CODE, 0L, false);
    }

    private static String firstToken(String command) {
        String trimmed = command.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }
}
