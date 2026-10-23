package dev.omatheusmesmo.qlawkus.tracing;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Gives one tool call its own span, so a ReAct turn reads as a flame graph: how many tools ran, in
 * which order, and which one stalled.
 *
 * <p>Upstream {@code ToolSpanWrapper} already does this for the tools declared on the AI service, but
 * only around its own {@code QuarkusToolExecutor}. The {@code @QlawTool} extension tools are built on
 * {@code DefaultToolExecutor} by {@code QlawToolProvider}, so without this decorator they would be
 * the one part of the loop missing from the trace. The span name and attributes copy upstream's,
 * which follow the OpenTelemetry GenAI conventions, so both kinds of tool read the same and one query
 * finds them all. Arguments and result are opt-in through the same upstream properties, since either
 * can carry what the owner said.
 *
 * <p>The span is made current while the tool runs, so an HTTP call the tool makes nests under it.
 * Both interface methods are overridden for the reason given on {@code MeteredToolExecutor}.
 */
public class TracedToolExecutor implements ToolExecutor {

    private static final String SPAN_PREFIX = "langchain4j.tools.";

    private final String toolName;
    private final ToolExecutor delegate;
    private final Tracer tracer;
    private final boolean includeArguments;
    private final boolean includeResult;

    public TracedToolExecutor(String toolName, ToolExecutor delegate, Tracer tracer,
            boolean includeArguments, boolean includeResult) {
        this.toolName = toolName;
        this.delegate = delegate;
        this.tracer = tracer;
        this.includeArguments = includeArguments;
        this.includeResult = includeResult;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        return trace(request, () -> delegate.execute(request, memoryId), result -> false, result -> result);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        return trace(request, () -> delegate.executeWithContext(request, context),
                ToolExecutionResult::isError, ToolExecutionResult::resultText);
    }

    private <T> T trace(ToolExecutionRequest request, Supplier<T> call, Predicate<T> isError,
            Function<T, String> resultText) {
        Span span = tracer.spanBuilder(SPAN_PREFIX + toolName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("gen_ai.operation.name", "execute_tool")
                .setAttribute("gen_ai.tool.name", toolName)
                .setAttribute("gen_ai.tool.call.id", request.id())
                .setAttribute("gen_ai.tool.type", "function")
                .startSpan();
        if (includeArguments) {
            span.setAttribute("gen_ai.tool.call.arguments", request.arguments());
        }
        try (Scope ignored = span.makeCurrent()) {
            T result = call.get();
            if (result != null && isError.test(result)) {
                span.setStatus(StatusCode.ERROR);
            }
            if (includeResult && result != null) {
                span.setAttribute("gen_ai.tool.call.result", resultText.apply(result));
            }
            return result;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }
}
