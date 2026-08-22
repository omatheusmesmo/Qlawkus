package dev.omatheusmesmo.qlawkus.metrics;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;

import java.time.Duration;

/**
 * Times one tool and records whether it succeeded, tagged by tool name.
 *
 * <p>Per-tool rather than aggregate on purpose: with roughly eleven tool integrations, an aggregate
 * error rate tells the owner something is broken without saying which, which is the least useful
 * shape for the metric to take.
 *
 * <p>Both interface methods are overridden. {@code DefaultToolExecutor} implements each of them
 * separately, so inheriting the interface default here would quietly route the richer
 * {@code executeWithContext} path through plain {@code execute} and discard the structured result.
 *
 * <p>This decorator is applied in {@code QlawToolProvider}, which is the only seam available: that
 * provider calls {@code ClientProxy.unwrap} before building each executor, so tool calls never cross
 * a CDI proxy and no interceptor, including the project's own {@code @Logged}, can observe them.
 */
public class MeteredToolExecutor implements ToolExecutor {

    private final String toolName;
    private final ToolExecutor delegate;
    private final AgentMeters meters;

    public MeteredToolExecutor(String toolName, ToolExecutor delegate, AgentMeters meters) {
        this.toolName = toolName;
        this.delegate = delegate;
        this.meters = meters;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        long startedAt = System.nanoTime();
        boolean succeeded = false;
        try {
            String result = delegate.execute(request, memoryId);
            succeeded = true;
            return result;
        } finally {
            record(startedAt, succeeded);
        }
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        long startedAt = System.nanoTime();
        boolean succeeded = false;
        try {
            ToolExecutionResult result = delegate.executeWithContext(request, context);
            succeeded = true;
            return result;
        } finally {
            record(startedAt, succeeded);
        }
    }

    private void record(long startedAt, boolean succeeded) {
        meters.tool(toolName, Duration.ofNanos(System.nanoTime() - startedAt), succeeded);
    }
}
