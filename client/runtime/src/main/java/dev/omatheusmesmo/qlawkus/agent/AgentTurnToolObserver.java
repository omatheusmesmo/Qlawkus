package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Adds each tool a synchronous turn runs to that turn's log line. It listens to the AI service's own
 * tool event rather than to the {@code QlawToolProvider} decorators because the tools declared on the
 * service itself (memory, skills, profile) never pass through those decorators. The event carries the
 * memory id, which is how it finds the open turn; a streamed turn lists its tools from the stream, so
 * it is skipped here to keep each tool counted once.
 */
@ApplicationScoped
public class AgentTurnToolObserver {

    void onToolExecuted(@Observes ToolExecutedEvent event) {
        Object memoryId = event.invocationContext() == null ? null : event.invocationContext().chatMemoryId();
        AgentTurn.forConversation(memoryId == null ? null : String.valueOf(memoryId))
                .filter(turn -> !turn.streamed())
                .ifPresent(turn -> turn.tool(event.request().name()));
    }
}
