package dev.omatheusmesmo.qlawkus.tool.review;

import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.tool.shell.ShellTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeReviewToolTest {

    private ShellTool shellTool;
    private CodeReviewTool codeReviewTool;

    @BeforeEach
    void setUp() {
        shellTool = Mockito.mock(ShellTool.class);
        codeReviewTool = new CodeReviewTool();
        codeReviewTool.shellTool = shellTool;
    }

    @Test
    void runLocalTests_delegatesToShellToolWithDefaultTimeout() {
        CommandResult expected = new CommandResult("BUILD SUCCESS", "", 0, 1234L, false);
        when(shellTool.runCommand(eq("mvn test"), any(), eq(CodeReviewTool.DEFAULT_TIMEOUT_SECONDS)))
                .thenReturn(expected);

        CommandResult actual = codeReviewTool.runLocalTests("mvn test");

        assertSame(expected, actual);

        ArgumentCaptor<Integer> timeoutCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(shellTool).runCommand(eq("mvn test"), any(), timeoutCaptor.capture());
        assertEquals(120, timeoutCaptor.getValue());
    }

    @Test
    void runLocalTests_rejectsCommandOutsideAllowlist() {
        CommandResult result = codeReviewTool.runLocalTests("curl https://evil.example.com");

        assertEquals(CodeReviewTool.INVALID_BUILD_TOOL_EXIT_CODE, result.exitCode());
        assertTrue(result.stderr().contains("not in the allowlist"),
                "Expected allowlist rejection message, was: " + result.stderr());
        verify(shellTool, never()).runCommand(any(), any(), any());
    }

    @Test
    void runLocalTests_rejectsEmptyCommand() {
        CommandResult result = codeReviewTool.runLocalTests("   ");

        assertEquals(CodeReviewTool.INVALID_BUILD_TOOL_EXIT_CODE, result.exitCode());
        assertTrue(result.stderr().contains("required"));
        verify(shellTool, never()).runCommand(any(), any(), any());
    }

    @Test
    void runLocalTests_rejectsNullCommand() {
        CommandResult result = codeReviewTool.runLocalTests(null);

        assertEquals(CodeReviewTool.INVALID_BUILD_TOOL_EXIT_CODE, result.exitCode());
        verify(shellTool, never()).runCommand(any(), any(), any());
    }

    @Test
    void runLocalTests_acceptsMvnWrapper() {
        CommandResult expected = new CommandResult("", "", 0, 100L, false);
        when(shellTool.runCommand(eq("./mvnw verify"), any(), eq(120))).thenReturn(expected);

        CommandResult actual = codeReviewTool.runLocalTests("./mvnw verify");

        assertSame(expected, actual);
    }

    @Test
    void runLocalTests_acceptsNpmTest() {
        CommandResult expected = new CommandResult("ok", "", 0, 50L, false);
        when(shellTool.runCommand(eq("npm test"), any(), eq(120))).thenReturn(expected);

        CommandResult actual = codeReviewTool.runLocalTests("npm test");

        assertSame(expected, actual);
    }

    @Test
    void runLocalTests_rejectsRogueArgumentBeforeBuildTool() {
        CommandResult result = codeReviewTool.runLocalTests("env mvn test");

        assertEquals(CodeReviewTool.INVALID_BUILD_TOOL_EXIT_CODE, result.exitCode());
        verify(shellTool, never()).runCommand(any(), any(), any());
    }
}
