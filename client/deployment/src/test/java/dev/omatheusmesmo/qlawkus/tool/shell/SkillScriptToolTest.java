package dev.omatheusmesmo.qlawkus.tool.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.config.SkillScriptsConfig;
import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.skill.SkillScripts;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * Pins what a skill script run hands to the shell and what it refuses. The shell is a mock, so the
 * assertions are about the exact command, the working directory and the timeout the tool chose, and about
 * the cases that must never reach the shell at all.
 */
@DisabledOnOs(OS.WINDOWS)
class SkillScriptToolTest {

    @TempDir
    Path skillDir;

    private SkillStore skills;
    private ShellTool shell;
    private SkillScriptsConfig config;
    private SkillScriptTool tool;
    private Path scriptsDir;

    @BeforeEach
    void setUp() throws IOException {
        scriptsDir = Files.createDirectories(skillDir.resolve("scripts"));
        Files.writeString(scriptsDir.resolve("run.sh"), "#!/bin/sh\n");
        Files.createDirectories(scriptsDir.resolve("lib"));
        Files.writeString(scriptsDir.resolve("lib/util.sh"), "#!/bin/sh\n");
        skills = mock(SkillStore.class);
        shell = mock(ShellTool.class);
        config = mock(SkillScriptsConfig.class);
        when(config.enabled()).thenReturn(true);
        when(config.timeout()).thenReturn(Duration.ofSeconds(60));
        when(shell.runCommand(anyString(), any(), any())).thenReturn(new CommandResult("ok", "", 0, 1, false));
        tool = new SkillScriptTool();
        tool.skills = skills;
        tool.config = config;
        tool.shell = shell;
    }

    private void skillHasScripts(boolean installed) {
        when(skills.scriptsOf("triage")).thenReturn(Optional.of(new SkillScripts(scriptsDir, installed)));
    }

    private String commandRun() {
        ArgumentCaptor<String> command = ArgumentCaptor.forClass(String.class);
        verify(shell).runCommand(command.capture(), isNull(), any());
        return command.getValue();
    }

    @Test
    void anOwnScriptRunsThroughTheShellAsItsRealPathWithQuotedArguments() throws IOException {
        skillHasScripts(false);

        CommandResult result = tool.runSkillScript("triage", "run.sh", List.of("plain", "it's here", "$(id)"), null);

        assertEquals(0, result.exitCode());
        assertEquals("'" + scriptsDir.toRealPath().resolve("run.sh") + "' 'plain' 'it'\\''s here' '$(id)'",
                commandRun());
    }

    @Test
    void aScriptInASubfolderRuns() throws IOException {
        skillHasScripts(false);

        tool.runSkillScript("triage", "lib/util.sh", null, null);

        assertTrue(commandRun().startsWith("'" + scriptsDir.toRealPath().resolve("lib/util.sh") + "'"));
    }

    @Test
    void theTimeoutIsLoweredToTheConfiguredMaximumAndDefaultsToIt() {
        skillHasScripts(false);

        tool.runSkillScript("triage", "run.sh", null, 3600);
        tool.runSkillScript("triage", "run.sh", null, null);
        tool.runSkillScript("triage", "run.sh", null, 5);

        ArgumentCaptor<Integer> timeouts = ArgumentCaptor.forClass(Integer.class);
        verify(shell, times(3)).runCommand(anyString(), isNull(), timeouts.capture());
        assertEquals(List.of(60, 60, 5), timeouts.getAllValues());
    }

    @Test
    void aPathThatLeavesTheScriptsFolderNeverReachesTheShell() throws IOException {
        skillHasScripts(false);
        Path outside = Files.writeString(skillDir.resolve("secret.sh"), "#!/bin/sh\n");
        Files.createSymbolicLink(scriptsDir.resolve("link.sh"), outside);

        for (String escape : List.of("../secret.sh", "/etc/passwd", "lib/../../secret.sh", "link.sh", "lib", "",
                "..\\secret.sh", SkillScripts.INSTALLED_MARKER, "missing.sh")) {
            CommandResult result = tool.runSkillScript("triage", escape, null, null);

            assertEquals(ShellTool.SECURITY_BLOCK_EXIT_CODE, result.exitCode(), escape);
        }
        verify(shell, never()).runCommand(anyString(), any(), anyInt());
    }

    @Test
    void theOwnToolRefusesInstalledScriptsAndTheInstalledToolRefusesOwnOnes() {
        skillHasScripts(true);
        CommandResult own = tool.runSkillScript("triage", "run.sh", null, null);
        skillHasScripts(false);
        CommandResult installed = tool.runInstalledSkillScript("triage", "run.sh", null, null);

        assertEquals(ShellTool.SECURITY_BLOCK_EXIT_CODE, own.exitCode());
        assertTrue(own.stderr().contains(SkillScriptTool.INSTALLED_TOOL), own.stderr());
        assertEquals(ShellTool.SECURITY_BLOCK_EXIT_CODE, installed.exitCode());
        assertTrue(installed.stderr().contains(SkillScriptTool.OWN_TOOL), installed.stderr());
        verify(shell, never()).runCommand(anyString(), any(), anyInt());
    }

    @Test
    void anInstalledSkillScriptRunsThroughTheInstalledTool() {
        skillHasScripts(true);

        CommandResult result = tool.runInstalledSkillScript("triage", "run.sh", null, null);

        assertEquals(0, result.exitCode());
    }

    @Test
    void aSkillWithoutScriptsOrWithTheFeatureOffNeverReachesTheShell() {
        when(skills.scriptsOf("triage")).thenReturn(Optional.empty());
        assertEquals(ShellTool.SECURITY_BLOCK_EXIT_CODE, tool.runSkillScript("triage", "run.sh", null, null).exitCode());

        skillHasScripts(false);
        when(config.enabled()).thenReturn(false);
        assertEquals(ShellTool.SECURITY_BLOCK_EXIT_CODE, tool.runSkillScript("triage", "run.sh", null, null).exitCode());

        verify(shell, never()).runCommand(anyString(), any(), anyInt());
    }

    @Test
    void tooManyOrTooLongArgumentsAreRefused() {
        skillHasScripts(false);

        CommandResult many = tool.runSkillScript("triage", "run.sh", Collections.nCopies(33, "a"), null);
        CommandResult long1 = tool.runSkillScript("triage", "run.sh", List.of("x".repeat(4097)), null);

        assertEquals(ShellTool.SECURITY_BLOCK_EXIT_CODE, many.exitCode());
        assertEquals(ShellTool.SECURITY_BLOCK_EXIT_CODE, long1.exitCode());
        verify(shell, never()).runCommand(anyString(), any(), anyInt());
    }
}
