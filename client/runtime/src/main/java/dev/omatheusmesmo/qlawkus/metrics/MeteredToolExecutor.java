package dev.omatheusmesmo.qlawkus.metrics;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import io.quarkus.logging.Log;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jboss.logmanager.MDC;

/**
 * Times one tool, records whether it succeeded, tagged by tool name, and writes one INFO line per call
 * with its name, duration and outcome. Arguments and results never reach the line.
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
 * a CDI proxy and no interceptor, including the project's own {@code @Logged}, can observe them. It is
 * also where the {@code tool} MDC key is set for the call. The turn's own list of tools comes from the
 * AI service's tool event instead ({@code AgentTurnToolObserver}), which also sees the tools declared on
 * the service.
 */
public class MeteredToolExecutor implements ToolExecutor {

    private static final String TOOL_KEY = "tool";

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
        return observed(() -> delegate.execute(request, memoryId), result -> false);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        return observed(() -> delegate.executeWithContext(request, context),
                ToolExecutionResult::isError);
    }

    private <T> T observed(Supplier<T> call, Predicate<T> isError) {
        long startedAt = System.nanoTime();
        boolean threw = true;
        boolean errored = true;
        MDC.put(TOOL_KEY, toolName);
        try {
            T result = call.get();
            threw = false;
            errored = result != null && isError.test(result);
            return result;
        } finally {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            meters.tool(toolName, elapsed, !threw);
            Log.infof("Tool call %s outcome=%s duration=%dms", toolName,
                    threw || errored ? "error" : "ok", elapsed.toMillis());
            MDC.remove(TOOL_KEY);
        }
    }
}
