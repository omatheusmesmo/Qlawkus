package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ApprovalToolExecutorTest {

    private static final String ARGS = "{\"to\":\"ana@example.com\"}";

    @Test
    void aToolThatIsNotNamedInTheConfigurationRunsExactlyAsBefore() {
        CountingExecutor delegate = new CountingExecutor();
        ApprovalToolExecutor gate = gate(delegate, name -> false, new RecordingApprovals());

        String result = gate.execute(request("sendEmail"), "telegram:42");

        assertEquals("delegate-ran", result);
        assertEquals(1, delegate.calls);
    }

    @Test
    void aGatedToolIsNotRunAndTheModelIsToldWhyOnTheExecutePath() {
        CountingExecutor delegate = new CountingExecutor();
        RecordingApprovals approvals = new RecordingApprovals();
        ApprovalToolExecutor gate = gate(delegate, "sendEmail"::equals, approvals);

        String result = gate.execute(request("sendEmail"), "telegram:42");

        assertEquals(0, delegate.calls);
        assertEquals("pending-text", result);
        assertEquals("sendEmail", approvals.toolName);
        assertEquals(ARGS, approvals.arguments);
        assertEquals("telegram:42", approvals.memoryId);
    }

    @Test
    void aGatedToolIsNotRunAndTheModelIsToldWhyOnTheContextPath() {
        CountingExecutor delegate = new CountingExecutor();
        ApprovalToolExecutor gate = gate(delegate, "sendEmail"::equals, new RecordingApprovals());

        ToolExecutionResult result = gate.executeWithContext(request("sendEmail"), null);

        assertEquals(0, delegate.calls);
        assertFalse(result.isError());
        assertEquals("pending-text", result.resultText());
    }

    @Test
    void theContextPathRunsAnUngatedToolThroughTheDelegate() {
        CountingExecutor delegate = new CountingExecutor();
        ApprovalToolExecutor gate = gate(delegate, name -> false, new RecordingApprovals());

        ToolExecutionResult result = gate.executeWithContext(request("readFile"), null);

        assertEquals(1, delegate.calls);
        assertEquals("delegate-ran", result.resultText());
    }

    private static ApprovalToolExecutor gate(ToolExecutor delegate, Predicate<String> gated,
            ApprovalGate approvals) {
        return new ApprovalToolExecutor("sendEmail", delegate, gated, approvals, new AgentDeliveryContext());
    }

    private static ToolExecutionRequest request(String name) {
        return ToolExecutionRequest.builder().id("call-1").name(name).arguments(ARGS).build();
    }

    private static final class CountingExecutor implements ToolExecutor {

        int calls;

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            calls++;
            return "delegate-ran";
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            calls++;
            return ToolExecutionResult.builder().resultText("delegate-ran").build();
        }
    }

    private static final class RecordingApprovals implements ApprovalGate {

        String toolName;
        String arguments;
        String memoryId;

        @Override
        public String requestApproval(String toolName, String rawArguments, String memoryId,
                AgentDeliveryContext.Snapshot origin) {
            this.toolName = toolName;
            this.arguments = rawArguments;
            this.memoryId = memoryId;
            return "pending-text";
        }

        @Override
        public int pendingCount() {
            return 0;
        }
    }
}
