package dev.omatheusmesmo.qlawkus.messaging.progress;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.agent.QlawkusAgent;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import io.quarkiverse.langchain4j.observability.AiServiceSelector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes the AI service's tool events to the messaging turn that owns the memory id. A turn is
 * registered right before the agent call and removed right after it, so the events of REST, console
 * and background work, which have no registered turn, are ignored. The events fire on the thread
 * that runs the agent call, which is the thread the tool sees its delivery context on.
 */
@ApplicationScoped
public class ToolProgressRouter {

    @Inject
    SecretRedactor redactor;

    private final Map<String, ToolProgressTurn> turns = new ConcurrentHashMap<>();

    public ToolProgressTurn begin(String memoryId, ToolProgressReporter reporter) {
        ToolProgressTurn turn = new ToolProgressTurn(reporter);
        turns.put(memoryId, turn);
        return turn;
    }

    public void end(String memoryId, ToolProgressTurn turn) {
        turns.remove(memoryId, turn);
    }

    void onAgentModelResponse(@Observes @AiServiceSelector(AgentService.class) AiServiceResponseReceivedEvent event) {
        modelResponded(event);
    }

    void onRunnerModelResponse(@Observes @AiServiceSelector(QlawkusAgent.class) AiServiceResponseReceivedEvent event) {
        modelResponded(event);
    }

    void onAgentToolExecuted(@Observes @AiServiceSelector(AgentService.class) ToolExecutedEvent event) {
        toolExecuted(event);
    }

    void onRunnerToolExecuted(@Observes @AiServiceSelector(QlawkusAgent.class) ToolExecutedEvent event) {
        toolExecuted(event);
    }

    private void modelResponded(AiServiceResponseReceivedEvent event) {
        ToolProgressTurn turn = turnFor(event.invocationContext());
        if (turn == null || event.response() == null || event.response().aiMessage() == null) {
            return;
        }
        List<ToolExecutionRequest> requests = event.response().aiMessage().toolExecutionRequests();
        if (requests == null) {
            return;
        }
        for (ToolExecutionRequest request : requests) {
            turn.toolRequested(request.id(), request.name(), redactedArguments(request));
        }
    }

    private void toolExecuted(ToolExecutedEvent event) {
        ToolProgressTurn turn = turnFor(event.invocationContext());
        if (turn != null) {
            turn.toolExecuted(event.request().id(), event.request().name());
        }
    }

    private ToolProgressTurn turnFor(InvocationContext context) {
        if (context == null || context.chatMemoryId() == null) {
            return null;
        }
        return turns.get(String.valueOf(context.chatMemoryId()));
    }

    private String redactedArguments(ToolExecutionRequest request) {
        return request.arguments() == null ? null : redactor.redact(request.arguments());
    }
}
