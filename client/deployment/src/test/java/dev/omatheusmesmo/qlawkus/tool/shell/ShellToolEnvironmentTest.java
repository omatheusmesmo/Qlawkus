package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
class ShellToolEnvironmentTest {

    static final String SECRET_VALUE = "shell-secret-value-0123456789";

    static final Set<String> SHELL_OWN_VARIABLES = Set.of("PWD", "OLDPWD", "SHLVL", "_");

    @Inject
    @QlawTool
    ShellTool shellTool;

    @Inject
    ShellConfig shellConfig;

    @Inject
    ChildEnvironment childEnvironment;

    @Inject
    WorkspaceConfinement workspaceConfinement;

    @Test
    void env_listsOnlyAllowlistedWorkspaceAndPassedThroughNames() {
        CommandResult result = shellTool.runCommand("env", null, null);

        Set<String> permitted = new HashSet<>(SHELL_OWN_VARIABLES);
        permitted.addAll(workspaceConfinement.getWorkspaceEnv().keySet());
        permitted.addAll(childEnvironment.configuredSecrets().keySet());
        permitted.addAll(List.of("GIT_CONFIG_COUNT", "GIT_CONFIG_KEY_0", "GIT_CONFIG_VALUE_0",
                "GIT_CONFIG_KEY_1", "GIT_CONFIG_VALUE_1", "GIT_TERMINAL_PROMPT"));

        List<String> leaked = new ArrayList<>();
        for (String line : result.stdout().split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = line.substring(0, eq);
            boolean allowed = permitted.contains(name)
                    || shellConfig.envAllowlist().stream().anyMatch(rule -> matches(rule, name));
            if (!allowed) {
                leaked.add(name);
            }
        }
        assertTrue(leaked.isEmpty(), "variables that should not reach the command: " + leaked);
    }

    @Test
    void env_doesNotExposeAProcessVariableThatIsNotAllowlisted() {
        String victim = System.getenv().keySet().stream()
                .filter(name -> shellConfig.envAllowlist().stream().noneMatch(rule -> matches(rule, name)))
                .filter(name -> !SHELL_OWN_VARIABLES.contains(name))
                .filter(name -> !childEnvironment.configuredSecrets().containsKey(name))
                .filter(name -> name.matches("[A-Z_][A-Z0-9_]*"))
                .findFirst()
                .orElse(null);
        Assumptions.assumeTrue(victim != null, "the build environment has no non-allowlisted variable");

        CommandResult result = shellTool.runCommand("printenv " + victim, null, null);

        assertEquals(1, result.exitCode(), "printenv exits 1 for an unset name");
        assertTrue(result.stdout().isBlank());
    }

    @Test
    void allowlistedPathReachesTheCommand() {
        CommandResult result = shellTool.runCommand("printenv PATH", null, null);

        assertEquals(0, result.exitCode());
        assertFalse(result.stdout().isBlank());
    }

    @Test
    void workspaceEnvFileValuesReachTheCommand() throws Exception {
        Path envFile = workspaceConfinement.getWorkspacePath().resolve(".qlawkus.env");
        Assumptions.assumeFalse(Files.exists(envFile), "a real env file is present, leaving it alone");
        try {
            Files.writeString(envFile, "WORKSPACE_ENV_PROBE=from-workspace\n");
            workspaceConfinement.reloadEnv();

            CommandResult result = shellTool.runCommand("printenv WORKSPACE_ENV_PROBE", null, null);

            assertEquals("from-workspace", result.stdout().trim());
        } finally {
            Files.deleteIfExists(envFile);
            workspaceConfinement.reloadEnv();
        }
    }

    @Test
    void passedThroughSecretIsVisibleToTheCommandButRedactedInTheOutput() {
        CommandResult length = shellTool.runCommand("printf %s \"$QLAWKUS_TEST_SECRET\" | wc -c", null, null);
        assertEquals(String.valueOf(SECRET_VALUE.length()), length.stdout().trim());

        CommandResult echoed = shellTool.runCommand("echo \"token=$QLAWKUS_TEST_SECRET\"", null, null);
        assertEquals("token=[redacted]", echoed.stdout().trim());
        assertFalse(echoed.stdout().contains(SECRET_VALUE));

        CommandResult onStderr = shellTool.runCommand("echo \"$QLAWKUS_TEST_SECRET\" >&2", null, null);
        assertFalse(onStderr.stderr().contains(SECRET_VALUE));
        assertTrue(onStderr.stderr().contains("[redacted]"));
    }

    @Test
    void envOutputRedactsThePassedThroughSecret() {
        CommandResult result = shellTool.runCommand("env", null, null);

        assertFalse(result.stdout().contains(SECRET_VALUE));
        assertTrue(result.stdout().contains("QLAWKUS_TEST_SECRET=[redacted]"), result.stdout());
    }

    @Test
    void auditLogNeverCarriesTheSecretNorTheCommandTextThatHoldsIt() {
        Logger logger = Logger.getLogger(ShellTool.class.getName());
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                messages.add(logRecord.getMessage() + " " + String.join(" ",
                        Arrays.stream(logRecord.getParameters() == null
                                ? new Object[0] : logRecord.getParameters()).map(String::valueOf).toList()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            shellTool.runCommand("echo " + SECRET_VALUE, null, null);
        } finally {
            logger.removeHandler(handler);
        }

        String audit = messages.stream().filter(m -> m.contains("SHELL_CMD")).findFirst().orElse("");
        assertFalse(audit.isEmpty(), "expected a SHELL_CMD audit line, got " + messages);
        assertFalse(audit.contains(SECRET_VALUE), audit);
        assertTrue(audit.contains("[redacted]"), audit);
    }

    @Test
    void passThroughWiredToAnUnsetVariableLeavesTheNameOutOfTheEnvironment() {
        CommandResult result = shellTool.runCommand("printenv UNSET_PASS_THROUGH", null, null);

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().isBlank());
    }

    @Test
    void githubTokenShapedOutputIsRedactedEvenWhenNotConfigured() {
        String token = "ghp_" + "Q1w2E3r4".repeat(5);

        CommandResult result = shellTool.runCommand("echo " + token, null, null);

        assertEquals("[redacted]", result.stdout().trim());
    }

    @Test
    void ghTokenPassThroughConfiguresGitCredentialsWithNothingOnDisk() {
        CommandResult gitVersion = shellTool.runCommand("git --version", null, null);
        Assumptions.assumeTrue(gitVersion.exitCode() == 0, "git is not available");

        CommandResult generic = shellTool.runCommand(
                "git config --show-scope --get-all credential.helper | tail -n 1", null, null);
        CommandResult github = shellTool.runCommand(
                "git config --show-scope --get-all credential.https://github.com.helper | tail -n 1", null, null);

        assertEquals("command", generic.stdout().replace("\n", "").trim(), generic.stdout() + generic.stderr());
        assertEquals("command\t!gh auth git-credential", github.stdout().trim(),
                github.stdout() + github.stderr());
    }

    private static boolean matches(String rule, String name) {
        return rule.endsWith("*") ? name.startsWith(rule.substring(0, rule.length() - 1)) : rule.equals(name);
    }
}
