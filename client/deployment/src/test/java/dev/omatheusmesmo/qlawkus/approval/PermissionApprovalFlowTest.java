package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.permission.PermissionAuditLog;
import dev.omatheusmesmo.qlawkus.permission.PermissionChangeAction;
import dev.omatheusmesmo.qlawkus.permission.PermissionLevel;
import dev.omatheusmesmo.qlawkus.permission.PermissionPolicy;
import dev.omatheusmesmo.qlawkus.permission.PermissionService;
import dev.omatheusmesmo.qlawkus.permission.PermissionState;
import dev.omatheusmesmo.qlawkus.permission.PermissionStateFile;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import dev.omatheusmesmo.qlawkus.tool.access.AccessSource;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessEntry;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessRegistry;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole loosening path with the real approval service: a chat can only ask, and only the owner's
 * decision on the recorded request changes the policy.
 */
class PermissionApprovalFlowTest {

    private static final AgentDeliveryContext.Snapshot ORIGIN =
            new AgentDeliveryContext.Snapshot(null, "telegram", "42");
    private static final PermissionService.Actor CHAT = PermissionService.Actor.chat("telegram", "42");
    private static final ToolAccessRegistry REGISTRY = new ToolAccessRegistry(List.of(
            new ToolAccessEntry("deleteEvent", AccessClass.DELETE, AccessSource.INFERRED,
                    "dev.omatheusmesmo.qlawkus.tools.google.calendar.CalendarTool#deleteEvent")));

    @TempDir
    Path root;

    private FileApprovalStore store;
    private PermissionPolicy policy;
    private PermissionService permissions;
    private ApprovalService approvals;

    @BeforeEach
    void setUp() {
        store = new FileApprovalStore(root.resolve("approvals"), 100);
        PermissionStateFile file = new PermissionStateFile(root.resolve("permissions"));
        policy = new PermissionPolicy(PermissionState.empty(), List.of(), REGISTRY, file);
        List<OwnerAction> actions = new ArrayList<>();
        approvals = new ApprovalService(store, new Config(), name -> Optional.empty(), new SilentNotifier(), text -> text,
                Runnable::run, new AgentDeliveryContext(), AgentMeters.disabled(), Clock.systemUTC(), actions,
                policy);
        permissions = new PermissionService(policy, file, new PermissionAuditLog(root.resolve("permissions"), 50),
                approvals, tool -> Optional.empty(), Clock.systemUTC());
        actions.add(new PermissionChangeAction(permissions));
        permissions.set("calendar", "delete", "deny", PermissionService.Actor.admin("admin"), ORIGIN);
    }

    @Test
    void aLooseningAskedFromChatChangesNothingUntilTheOwnerApprovesAndThenAppliesExactlyOnce() {
        PermissionService.Outcome asked = permissions.set("calendar", "delete", "ask", CHAT, ORIGIN);

        assertEquals(PermissionService.Status.PENDING, asked.status());
        assertEquals(PermissionLevel.DENY, policy.levelOf("deleteEvent"));
        ApprovalRequest pending = store.find(asked.requestId()).orElseThrow();
        assertEquals(ApprovalStatus.PENDING, pending.status());
        assertTrue(pending.isOwnerAction());

        ApprovalService.Decision first = approvals.approve(asked.requestId(), "messaging:telegram:42");
        ApprovalService.Decision replay = approvals.approve(asked.requestId(), "messaging:telegram:42");

        assertTrue(first.accepted());
        assertFalse(replay.accepted());
        assertEquals(PermissionLevel.ASK, policy.levelOf("deleteEvent"));
        assertEquals(ApprovalStatus.EXECUTED, store.find(asked.requestId()).orElseThrow().status());
        long applied = permissions.audit().stream().filter(entry -> asked.requestId().equals(entry.requestId())).count();
        assertEquals(1, applied);
    }

    @Test
    void aDeniedLooseningNeverChangesThePolicy() {
        PermissionService.Outcome asked = permissions.set("calendar", "delete", "allow", CHAT, ORIGIN);

        approvals.deny(asked.requestId(), "messaging:telegram:42");

        assertEquals(PermissionLevel.DENY, policy.levelOf("deleteEvent"));
        assertEquals(ApprovalStatus.DENIED, store.find(asked.requestId()).orElseThrow().status());
    }

    @Test
    void theApprovedChangeIsRefusedWhenTheCellMovedSinceItWasAsked() {
        PermissionService.Outcome asked = permissions.set("calendar", "delete", "allow", CHAT, ORIGIN);
        permissions.set("calendar", "delete", "ask", PermissionService.Actor.admin("admin"), ORIGIN);

        approvals.approve(asked.requestId(), "owner");

        assertEquals(PermissionLevel.ASK, policy.levelOf("deleteEvent"));
        assertEquals(ApprovalStatus.FAILED, store.find(asked.requestId()).orElseThrow().status());
    }

    private static final class SilentNotifier implements ApprovalNotifier {
        @Override
        public void notifyOrigin(ApprovalRequest request, String text) {
        }

        @Override
        public void decided(ApprovalRequest request) {
        }
    }

    private static final class Config implements ApprovalConfig {
        @Override
        public Optional<List<String>> tools() {
            return Optional.empty();
        }

        @Override
        public Duration expiry() {
            return Duration.ofHours(24);
        }

        @Override
        public int maxPending() {
            return 10;
        }

        @Override
        public int previewMaxChars() {
            return 200;
        }
    }
}
