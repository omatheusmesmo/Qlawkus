package dev.omatheusmesmo.qlawkus.heartbeat;

import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccess;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.config.AdminAuthConfig;
import dev.omatheusmesmo.qlawkus.task.OwnerChatDirectory;
import dev.omatheusmesmo.qlawkus.task.TaskAudit;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@QlawTool(capturePayload = false)
@ApplicationScoped
public class HeartbeatTool {

    private final HeartbeatService service;
    private final AgentDeliveryContext delivery;
    private final OwnerChatDirectory ownerChats;
    private final AdminAuthConfig auth;

    @Inject
    HeartbeatTool(HeartbeatService service, AgentDeliveryContext delivery, OwnerChatDirectory ownerChats, AdminAuthConfig auth) {
        this.service = service;
        this.delivery = delivery;
        this.ownerChats = ownerChats;
        this.auth = auth;
    }

    @ToolAccess(AccessClass.READ)
    @Tool("Show heartbeat state, budgets and known notification providers. The heartbeat is an autonomous read-only check, "
            + "and scheduled reminders use scheduleTask.")
    public String heartbeatStatus() {
        if (!directOwner()) {
            return "Heartbeat settings require a direct request in an authorized owner conversation.";
        }
        return describe(service.status());
    }

    @ToolAccess(AccessClass.UPDATE)
    @Tool("Save heartbeat settings only when the owner requests them in this conversation. The heartbeat checks for missed "
            + "or invalid schedules and whether curated memory exists. Put that choice in the instructions, and use "
            + "heartbeatStatus to find a provider already accepted for this owner. Do not reuse a task prompt as a heartbeat.")
    public String configureHeartbeat(
            @P("Whether to enable the heartbeat.") boolean enabled,
            @P("Interval in minutes, at least the configured minimum.") int intervalMinutes,
            @P("Owner-approved heartbeat instructions, up to 1000 characters.") String prompt,
            @P(value = "An accepted notification provider name. Omit for silent observation.", required = false) String provider) {
        if (!directOwner()) {
            return "Heartbeat settings require a direct request in an authorized owner conversation.";
        }
        try {
            return describe(service.configure(new HeartbeatSettings(enabled, intervalMinutes, prompt, provider)));
        } catch (IllegalArgumentException failure) {
            return failure.getMessage();
        } catch (IllegalStateException failure) {
            return "Heartbeat settings could not be saved.";
        }
    }

    private boolean directOwner() {
        if (TaskAudit.currentRun() != null) {
            return false;
        }
        var context = delivery.snapshot();
        if (context.authenticatedOwner() != null) {
            return auth.username().equals(context.authenticatedOwner());
        }
        return delivery.hasDeliveryInfo() && ownerChats.lastChat(context.providerId())
                .filter(chat -> context.chatId().equals(chat.chatId())).isPresent();
    }

    private static String describe(HeartbeatStatus status) {
        String last = status.lastRun() == null ? "none" : status.lastRun().outcome().name();
        return "Heartbeat %s. Interval %d min (minimum %d). Next run %s. Last outcome %s. "
                .concat("Remaining daily runs %d. Providers %s. Storage available %s.")
                .formatted(status.settings().enabled() ? "enabled" : "disabled", status.settings().intervalMinutes(),
                        status.minimumIntervalMinutes(), status.nextRun() == null ? "none" : status.nextRun(),
                        last, status.remainingDailyRuns(), status.availableProviders(), status.storageAvailable());
    }
}
