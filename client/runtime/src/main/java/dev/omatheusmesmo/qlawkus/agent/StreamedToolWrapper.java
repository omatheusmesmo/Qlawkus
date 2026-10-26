package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.function.BiFunction;

/**
 * Restores the streamed turn's context around the tools declared on {@code @RegisterAiService(tools = ...)}.
 * Those run through quarkus-langchain4j's own executor, which the {@code QlawToolProvider} decorators
 * never see, so this wrapper is the seam for them. It is the only extension point that executor exposes,
 * and it is consulted for every tool call, so the context is looked up per call by conversation and is a
 * no-op for a turn that did not stream.
 */
@ApplicationScoped
public class StreamedToolWrapper implements QuarkusToolExecutor.Wrapper {

    private final StreamedToolContext streamed;

    @Inject
    public StreamedToolWrapper(StreamedToolContext streamed) {
        this.streamed = streamed;
    }

    @Override
    public ToolExecutionResult wrap(ToolExecutionRequest request, InvocationContext context,
            BiFunction<ToolExecutionRequest, InvocationContext, ToolExecutionResult> next,
            QuarkusToolExecutor executor) {
        try (StreamedToolContext.Entered ignored = streamed.enter(context.chatMemoryId())) {
            return next.apply(request, context);
        }
    }
}
