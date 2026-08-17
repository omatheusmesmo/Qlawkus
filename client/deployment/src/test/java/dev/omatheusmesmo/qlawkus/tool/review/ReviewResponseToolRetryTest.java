package dev.omatheusmesmo.qlawkus.tool.review;

import dev.omatheusmesmo.qlawkus.dto.CommandResult;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import dev.omatheusmesmo.qlawkus.tool.shell.ShellTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Pins the classification logic in {@link ReviewResponseTool#runReviewCommand}: {@code gh} exposes no
 * structured status code, only text, so the transient/permanent split hinges entirely on
 * {@link ReviewResponseTool#TRANSIENT_FAILURE} matching correctly. {@code @Retry} itself is not
 * exercised here: {@link ShellTool} is {@code @Dependent}-scoped, which {@code @InjectMock} refuses to
 * mock, so this stays a plain unit test.
 */
class ReviewResponseToolRetryTest {

    private ShellTool shellTool;
    private ReviewResponseTool tool;

    @BeforeEach
    void setUp() {
        shellTool = Mockito.mock(ShellTool.class);
        tool = new ReviewResponseTool();
        tool.shellTool = shellTool;
    }

    @Test
    void aTransientLookingFailureThrowsTransientHttpException() {
        CommandResult failure = new CommandResult("", "HTTP 503: Service Unavailable", 1, 10L, false);
        when(shellTool.runCommand(anyString(), any(), anyInt())).thenReturn(failure);

        assertThrows(TransientHttpException.class,
                () -> tool.runReviewCommand("gh pr review 42 --approve"));
    }

    @Test
    void aPermanentFailureIsReturnedAsIs() {
        CommandResult failure = new CommandResult("", "could not find pull request", 1, 10L, false);
        when(shellTool.runCommand(anyString(), any(), anyInt())).thenReturn(failure);

        CommandResult result = tool.runReviewCommand("gh pr review 42 --approve");

        assertEquals(1, result.exitCode());
    }

    @Test
    void submitReview_exhaustsRetriesThenRejectsGracefully() {
        CommandResult failure = new CommandResult("", "HTTP 503: Service Unavailable", 1, 10L, false);
        when(shellTool.runCommand(anyString(), any(), anyInt())).thenReturn(failure);

        // No CDI here, so @Retry never fires - this pins submitReview's catch block instead: a
        // graceful CommandResult, never an exception escaping to the caller.
        CommandResult result = tool.submitReview(42, "APPROVE", null);

        assertEquals(-10, result.exitCode());
    }
}
