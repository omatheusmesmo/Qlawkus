package dev.omatheusmesmo.qlawkus.tool.quarantine;

import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import dev.omatheusmesmo.qlawkus.task.OwnerChatDirectory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Counts a quarantine and tells the owner in the chat they wrote from last, so a tool that quietly
 * vanished from the agent's toolbelt is never a surprise. With no known chat there is nobody to tell,
 * and the log line and the meter still carry it.
 */
@ApplicationScoped
public class ToolQuarantineNotifier {

    @Inject
    AgentMeters meters;

    @Inject
    OwnerChatDirectory ownerChats;

    @Inject
    Event<WorkflowResultReadyEvent> results;

    void onQuarantined(@ObservesAsync ToolQuarantinedEvent event) {
        meters.toolQuarantined(event.tool());
        Optional<OwnerChat> chat = ownerChats.mostRecent();
        chat.ifPresent(target -> results.fireAsync(
                new WorkflowResultReadyEvent(target.providerId(), target.chatId(), notice(event))));
    }

    static String notice(ToolQuarantinedEvent event) {
        long minutes = Math.max(1, Duration.between(Instant.now(), event.until()).toMinutes());
        return "The tool '" + event.tool() + "' failed " + event.failures()
                + " times in a row, so I took it out of my toolbelt. It comes back on probation in about "
                + minutes + " minutes, or sooner if you release it.";
    }
}
