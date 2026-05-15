package dev.omatheusmesmo.qlawkus.tool.review;

import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.tool.shell.ShellTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewResponseToolTest {

    private ShellTool shellTool;
    private ReviewResponseTool tool;

    @BeforeEach
    void setUp() {
        shellTool = Mockito.mock(ShellTool.class);
        tool = new ReviewResponseTool();
        tool.shellTool = shellTool;
    }

    @Test
    void submitReview_approve_sendsCorrectCommand() {
        CommandResult ok = new CommandResult("", "", 0, 100L, false);
        when(shellTool.runCommand(eq("gh pr review 42 --approve"), any(), eq(ReviewResponseTool.TIMEOUT_SECONDS)))
                .thenReturn(ok);

        CommandResult result = tool.submitReview(42, "APPROVE", null);

        assertSame(ok, result);
        verify(shellTool).runCommand(eq("gh pr review 42 --approve"), any(), eq(30));
    }

    @Test
    void submitReview_requestChanges_includesBody() {
        CommandResult ok = new CommandResult("", "", 0, 100L, false);
        when(shellTool.runCommand(startsWith("gh pr review 7 --request-changes --body"), any(), anyInt()))
                .thenReturn(ok);

        CommandResult result = tool.submitReview(7, "REQUEST_CHANGES", "Please fix the bug");

        assertSame(ok, result);
    }

    @Test
    void submitReview_requestChanges_withoutBody_rejects() {
        CommandResult result = tool.submitReview(1, "REQUEST_CHANGES", null);

        assertEquals(-10, result.exitCode());
        assertTrue(result.stderr().contains("body is required"));
        verify(shellTool, never()).runCommand(any(), any(), any());
    }

    @Test
    void submitReview_requestChanges_blankBody_rejects() {
        CommandResult result = tool.submitReview(1, "REQUEST_CHANGES", "   ");

        assertEquals(-10, result.exitCode());
        verify(shellTool, never()).runCommand(any(), any(), any());
    }

    @Test
    void submitReview_comment_withBody_includesBody() {
        CommandResult ok = new CommandResult("", "", 0, 50L, false);
        when(shellTool.runCommand(startsWith("gh pr review 99 --comment"), any(), anyInt()))
                .thenReturn(ok);

        CommandResult result = tool.submitReview(99, "COMMENT", "Looks good overall");

        assertSame(ok, result);
    }

    @Test
    void submitReview_unknownType_rejects() {
        CommandResult result = tool.submitReview(1, "LGTM", null);

        assertEquals(-10, result.exitCode());
        assertTrue(result.stderr().contains("Unknown review type"));
        verify(shellTool, never()).runCommand(any(), any(), any());
    }

    @Test
    void submitReview_reviewTypeIsCaseInsensitive() {
        CommandResult ok = new CommandResult("", "", 0, 100L, false);
        when(shellTool.runCommand(eq("gh pr review 5 --approve"), any(), anyInt())).thenReturn(ok);

        CommandResult result = tool.submitReview(5, "approve", null);

        assertSame(ok, result);
    }

    @Test
    void shellQuote_escapesInternalSingleQuotes() {
        String quoted = ReviewResponseTool.shellQuote("it's done");
        assertEquals("'it'\\''s done'", quoted);
    }
}
