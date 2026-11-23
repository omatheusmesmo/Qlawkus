package dev.omatheusmesmo.qlawkus.approval;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import java.util.function.Predicate;

/**
 * Stands between the model's call and a named tool. A tool the owner has put under approval is never
 * run from here: the call is recorded as pending and the model is told so. Any other tool passes
 * straight through to the executor it wraps.
 *
 * <p>Both interface methods are overridden, for the reason given on {@code MeteredToolExecutor}.
 */
public final class ApprovalToolExecutor implements ToolExecutor {

    private final String toolName;
    private final ToolExecutor delegate;
    private final Predicate<String> requiresApproval;
    private final ApprovalGate gate;
    private final AgentDeliveryContext delivery;

    public ApprovalToolExecutor(String toolName, ToolExecutor delegate, Predicate<String> requiresApproval,
            ApprovalGate gate, AgentDeliveryContext delivery) {
        this.toolName = toolName;
        this.delegate = delegate;
        this.requiresApproval = requiresApproval;
        this.gate = gate;
        this.delivery = delivery;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        if (!requiresApproval.test(toolName)) {
            return delegate.execute(request, memoryId);
        }
        return gate.requestApproval(toolName, request.arguments(), asText(memoryId), delivery.snapshot());
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        if (!requiresApproval.test(toolName)) {
            return delegate.executeWithContext(request, context);
        }
        String memoryId = context == null ? null : asText(context.chatMemoryId());
        String text = gate.requestApproval(toolName, request.arguments(), memoryId, delivery.snapshot());
        return ToolExecutionResult.builder().resultText(text).build();
    }

    private static String asText(Object memoryId) {
        return memoryId == null ? null : memoryId.toString();
    }
}
