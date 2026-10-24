package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.config.ShellConfig;
import dev.omatheusmesmo.qlawkus.dto.SessionOutput;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
class PtySessionEnvironmentTest {

    @Inject
    @QlawTool
    InteractiveShellTool interactiveShellTool;

    @Inject
    ShellConfig shellConfig;

    @Inject
    ChildEnvironment childEnvironment;

    @Inject
    WorkspaceConfinement workspaceConfinement;

    @Test
    void ptyEnvironmentIsBuiltByTheSameAllowlistAsOneShotCommands() throws Exception {
        String sessionId = interactiveShellTool.startSession("bash", null, null);
        try {
            interactiveShellTool.sendInput(sessionId, "env");
            Thread.sleep(1500);
            SessionOutput output = interactiveShellTool.readSession(sessionId, 0);

            Set<String> permitted = new HashSet<>(Set.of("PWD", "OLDPWD", "SHLVL", "_", "TERM",
                    "GIT_CONFIG_COUNT", "GIT_CONFIG_KEY_0", "GIT_CONFIG_VALUE_0",
                    "GIT_CONFIG_KEY_1", "GIT_CONFIG_VALUE_1", "GIT_TERMINAL_PROMPT", "COLUMNS", "LINES"));
            permitted.addAll(workspaceConfinement.getWorkspaceEnv().keySet());
            permitted.addAll(childEnvironment.configuredSecrets().keySet());

            List<String> leaked = new ArrayList<>();
            for (String line : output.lines()) {
                int eq = line.indexOf('=');
                if (eq <= 0 || !line.substring(0, eq).matches("[A-Z_][A-Z0-9_]*")) {
                    continue;
                }
                String name = line.substring(0, eq);
                boolean allowed = permitted.contains(name)
                        || shellConfig.envAllowlist().stream().anyMatch(rule -> matches(rule, name));
                if (!allowed) {
                    leaked.add(name);
                }
            }
            assertTrue(leaked.isEmpty(), "variables that should not reach the session: " + leaked);
            assertTrue(output.lines().stream().anyMatch(l -> l.equals("TERM=xterm-256color")),
                    "TERM must stay xterm-256color, got " + output.lines());
        } finally {
            interactiveShellTool.closeSession(sessionId);
        }
    }

    @Test
    void ptyOutputReadBackRedactsPassedThroughSecretsAndGithubTokens() throws Exception {
        String sessionId = interactiveShellTool.startSession("bash", null, null);
        try {
            interactiveShellTool.sendInput(sessionId, "echo \"v=$QLAWKUS_TEST_SECRET\"");
            interactiveShellTool.sendInput(sessionId, "echo " + "ghp_" + "Z9y8X7w6".repeat(5));
            Thread.sleep(1500);
            SessionOutput output = interactiveShellTool.readSession(sessionId, 0);

            String joined = String.join("\n", output.lines());
            assertFalse(joined.contains(ShellToolEnvironmentTest.SECRET_VALUE), joined);
            assertFalse(joined.contains("ghp_Z9y8"), joined);
            assertTrue(output.lines().stream().anyMatch(l -> l.equals("v=[redacted]")), joined);
            assertTrue(output.lines().stream().anyMatch(l -> l.equals("[redacted]")), joined);
            assertEquals(0, output.lines().stream().filter(l -> l.contains(ShellToolEnvironmentTest.SECRET_VALUE)).count());
        } finally {
            interactiveShellTool.closeSession(sessionId);
        }
    }

    private static boolean matches(String rule, String name) {
        return rule.endsWith("*") ? name.startsWith(rule.substring(0, rule.length() - 1)) : rule.equals(name);
    }
}
