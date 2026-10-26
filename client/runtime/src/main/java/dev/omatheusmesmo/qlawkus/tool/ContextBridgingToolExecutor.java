package dev.omatheusmesmo.qlawkus.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.omatheusmesmo.qlawkus.agent.StreamedToolContext;

/**
 * Runs one {@code @QlawTool} call inside the context of the streamed turn it belongs to, when there is
 * one. See {@link StreamedToolContext}; for a turn that did not stream the call passes straight through.
 *
 * <p>Both interface methods are overridden for the reason given on {@code MeteredToolExecutor}.
 */
public class ContextBridgingToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final StreamedToolContext streamed;

    public ContextBridgingToolExecutor(ToolExecutor delegate, StreamedToolContext streamed) {
        this.delegate = delegate;
        this.streamed = streamed;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        try (StreamedToolContext.Entered ignored = streamed.enter(memoryId)) {
            return delegate.execute(request, memoryId);
        }
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        try (StreamedToolContext.Entered ignored = streamed.enter(context.chatMemoryId())) {
            return delegate.executeWithContext(request, context);
        }
    }
}
