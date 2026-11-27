package dev.omatheusmesmo.qlawkus.permission;

import static dev.omatheusmesmo.qlawkus.permission.PermissionLevel.ALLOW;
import static dev.omatheusmesmo.qlawkus.permission.PermissionLevel.ASK;
import static dev.omatheusmesmo.qlawkus.permission.PermissionLevel.DENY;
import static dev.omatheusmesmo.qlawkus.permission.PermissionService.Status.APPLIED;
import static dev.omatheusmesmo.qlawkus.permission.PermissionService.Status.PENDING;
import static dev.omatheusmesmo.qlawkus.permission.PermissionService.Status.REJECTED;
import static dev.omatheusmesmo.qlawkus.permission.PermissionService.Status.UNCHANGED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.approval.ApprovalGate;
import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.ApprovalStatus;
import dev.omatheusmesmo.qlawkus.tool.access.AccessClass;
import dev.omatheusmesmo.qlawkus.tool.access.AccessSource;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessEntry;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessRegistry;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PermissionServiceTest {

    private static final AgentDeliveryContext.Snapshot ORIGIN =
            new AgentDeliveryContext.Snapshot(null, "telegram", "42");
    private static final PermissionService.Actor CHAT = PermissionService.Actor.chat("telegram", "42");
    private static final PermissionService.Actor ADMIN = PermissionService.Actor.admin("admin-api:qlawkus");

    private static final ToolAccessRegistry REGISTRY = new ToolAccessRegistry(List.of(
            entry("deleteEvent", AccessClass.DELETE, "tools.google.calendar.CalendarTool"),
            entry("listEvents", AccessClass.READ, "tools.google.calendar.CalendarTool"),
            entry("trashFile", AccessClass.DELETE, "tools.google.drive.DriveTool"),
            entry("sendEmail", AccessClass.SEND, "tools.google.gmail.GmailTool")));

    private static ToolAccessEntry entry(String tool, AccessClass accessClass, String bean) {
        return new ToolAccessEntry(tool, accessClass, AccessSource.INFERRED,
                "dev.omatheusmesmo.qlawkus." + bean + "#" + tool);
    }

    @TempDir
    Path root;

    private RecordingGate gate;
    private PermissionService service;
    private PermissionPolicy policy;

    @BeforeEach
    void setUp() {
        gate = new RecordingGate();
        build(PermissionState.empty());
    }

    private void build(PermissionState configured) {
        PermissionStateFile file = new PermissionStateFile(root.resolve("permissions"));
        policy = new PermissionPolicy(configured, List.of(), REGISTRY, file);
        service = new PermissionService(policy, file, new PermissionAuditLog(root.resolve("permissions"), 5), gate,
                tool -> Optional.empty(), Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), ZoneOffset.UTC));
    }

    private PermissionLevel levelOf(String tool) {
        return policy.levelOf(tool);
    }

    @Test
    void tighteningFromChatAppliesAtOnceAndAsksNobody() {
        PermissionService.Outcome outcome = service.set("calendar", "delete", "ask", CHAT, ORIGIN);

        assertEquals(APPLIED, outcome.status());
        assertEquals(ASK, levelOf("deleteEvent"));
        assertTrue(gate.requests.isEmpty());
    }

    @Test
    void everyTighteningStepAppliesFromChat() {
        assertEquals(APPLIED, service.set("calendar", "delete", "deny", CHAT, ORIGIN).status());
        assertEquals(DENY, levelOf("deleteEvent"));

        assertEquals(APPLIED, service.set("drive", "delete", "ask", CHAT, ORIGIN).status());
        assertEquals(APPLIED, service.set("drive", "delete", "deny", CHAT, ORIGIN).status());
        assertEquals(DENY, levelOf("trashFile"));
    }

    @Test
    void looseningFromChatIsNotAppliedAndWaitsForTheOwner() {
        service.set("calendar", "delete", "deny", CHAT, ORIGIN);

        PermissionService.Outcome outcome = service.set("calendar", "delete", "ask", CHAT, ORIGIN);

        assertEquals(PENDING, outcome.status());
        assertNotNull(outcome.requestId());
        assertEquals(DENY, levelOf("deleteEvent"), "nothing changes until the owner approves");
        assertEquals(1, gate.requests.size());
        assertTrue(gate.requests.get(0).preview().contains("deny -> ask"), gate.requests.get(0).preview());
        assertTrue(gate.requests.get(0).arguments().contains("\"from\":\"deny\""));
    }

    @Test
    void denyToAllowFromChatWaitsForTheOwner() {
        service.set("calendar", "delete", "deny", CHAT, ORIGIN);

        assertEquals(PENDING, service.set("calendar", "delete", "allow", CHAT, ORIGIN).status());
        assertEquals(DENY, levelOf("deleteEvent"));
    }

    @Test
    void askToAllowFromChatWaitsForTheOwner() {
        build(PermissionState.empty().withCell("calendar", AccessClass.DELETE, ASK));

        assertEquals(PENDING, service.set("calendar", "delete", "allow", CHAT, ORIGIN).status());
        assertEquals(ASK, levelOf("deleteEvent"));
    }

    @Test
    void anAdminMayLoosenDirectly() {
        service.set("calendar", "delete", "deny", ADMIN, ORIGIN);

        PermissionService.Outcome outcome = service.set("calendar", "delete", "allow", ADMIN, ORIGIN);

        assertEquals(APPLIED, outcome.status());
        assertEquals(ALLOW, levelOf("deleteEvent"));
        assertTrue(gate.requests.isEmpty());
    }

    @Test
    void settingTheLevelAlreadyInForceChangesNothing() {
        PermissionService.Outcome outcome = service.set("calendar", "delete", "allow", CHAT, ORIGIN);

        assertEquals(UNCHANGED, outcome.status());
        assertTrue(service.audit().isEmpty());
    }

    @Test
    void theApprovedLooseningAppliesOnceAndIsAudited() {
        service.set("calendar", "delete", "deny", CHAT, ORIGIN);
        service.set("calendar", "delete", "ask", CHAT, ORIGIN);
        ApprovalRequest request = gate.requestAsApproved("owner:telegram:42", "abc");

        String summary = service.applyApproved(request);

        assertEquals(ASK, levelOf("deleteEvent"));
        assertTrue(summary.contains("calendar") && summary.contains("ask"), summary);
        PermissionAuditEntry entry = service.audit().get(0);
        assertEquals("messaging:telegram:42", entry.actor());
        assertEquals("telegram", entry.channel());
        assertEquals("owner:telegram:42", entry.approvedBy());
        assertEquals("deny", entry.before());
        assertEquals("ask", entry.after());
        assertEquals("abc", entry.requestId());
    }

    @Test
    void anApprovedLooseningIsRefusedWhenTheCellChangedSinceItWasAsked() {
        service.set("calendar", "delete", "deny", CHAT, ORIGIN);
        service.set("calendar", "delete", "allow", CHAT, ORIGIN);
        ApprovalRequest request = gate.requestAsApproved("owner", "id1");
        service.set("calendar", "delete", "ask", ADMIN, ORIGIN);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> service.applyApproved(request));

        assertTrue(refused.getMessage().contains("changed"), refused.getMessage());
        assertEquals(ASK, levelOf("deleteEvent"));
    }

    @Test
    void presetThatOnlyTightensAppliesFromChat() {
        PermissionService.Outcome outcome = service.applyPreset("readonly", CHAT, ORIGIN);

        assertEquals(APPLIED, outcome.status());
        assertEquals(ALLOW, levelOf("listEvents"));
        assertEquals(DENY, levelOf("deleteEvent"));
        assertEquals(DENY, levelOf("sendEmail"));
    }

    @Test
    void presetThatLoosensAnythingWaitsForTheOwnerWhenItComesFromChat() {
        service.applyPreset("readonly", ADMIN, ORIGIN);

        PermissionService.Outcome outcome = service.applyPreset("trusted", CHAT, ORIGIN);

        assertEquals(PENDING, outcome.status());
        assertEquals(DENY, levelOf("deleteEvent"));
        assertEquals(1, gate.requests.size());
    }

    @Test
    void anApprovedPresetIsRefusedWhenThePolicyMovedOnSinceItWasAsked() {
        service.applyPreset("readonly", ADMIN, ORIGIN);
        service.applyPreset("trusted", CHAT, ORIGIN);
        ApprovalRequest request = gate.requestAsApproved("owner", "id2");
        service.set("drive", "delete", "ask", ADMIN, ORIGIN);

        assertThrows(IllegalStateException.class,
                () -> service.applyApproved(request));

        assertEquals(DENY, levelOf("deleteEvent"));
    }

    @Test
    void anAdminAppliesAnyPresetDirectlyAndAnUnknownOneIsRejected() {
        assertEquals(APPLIED, service.applyPreset("trusted", ADMIN, ORIGIN).status());
        assertEquals(REJECTED, service.applyPreset("paranoid", ADMIN, ORIGIN).status());
    }

    @Test
    void resetFromChatWaitsWhenItWouldLoosenAndAnAdminAppliesIt() {
        service.set("calendar", "delete", "deny", ADMIN, ORIGIN);

        assertEquals(PENDING, service.reset(CHAT, ORIGIN).status());
        assertEquals(DENY, levelOf("deleteEvent"));

        assertEquals(APPLIED, service.reset(ADMIN, ORIGIN).status());
        assertEquals(ALLOW, levelOf("deleteEvent"));
    }

    @Test
    void aToolExceptionFollowsTheSameAsymmetry() {
        assertEquals(APPLIED, service.setTool("deleteEvent", "deny", CHAT, ORIGIN).status());
        assertEquals(DENY, levelOf("deleteEvent"));
        assertEquals(ALLOW, levelOf("trashFile"));

        assertEquals(PENDING, service.setTool("deleteEvent", "allow", CHAT, ORIGIN).status());
        assertEquals(DENY, levelOf("deleteEvent"));
    }

    @Test
    void theOperationDefaultRowIsSetThroughTheDefaultGroup() {
        assertEquals(APPLIED, service.set("default", "send", "ask", CHAT, ORIGIN).status());

        assertEquals(ASK, levelOf("sendEmail"));
    }

    @Test
    void anUnknownGroupOperationOrLevelIsRejectedAndRecordsNothing() {
        assertEquals(REJECTED, service.set("calender", "delete", "ask", CHAT, ORIGIN).status());
        assertEquals(REJECTED, service.set("calendar", "remove", "ask", CHAT, ORIGIN).status());
        assertEquals(REJECTED, service.set("calendar", "delete", "maybe", CHAT, ORIGIN).status());
        assertEquals(REJECTED, service.setTool("bad name!", "ask", CHAT, ORIGIN).status());

        assertEquals(ALLOW, levelOf("deleteEvent"));
        assertTrue(service.audit().isEmpty());
    }

    @Test
    void whenTheApprovalQueueIsFullTheLooseningIsRefusedNotApplied() {
        service.set("calendar", "delete", "deny", CHAT, ORIGIN);
        gate.refuse = true;

        PermissionService.Outcome outcome = service.set("calendar", "delete", "allow", CHAT, ORIGIN);

        assertEquals(REJECTED, outcome.status());
        assertEquals(DENY, levelOf("deleteEvent"));
    }

    @Test
    void changesSurviveARestart() {
        service.set("calendar", "delete", "deny", ADMIN, ORIGIN);
        service.setTool("sendEmail", "ask", ADMIN, ORIGIN);

        build(PermissionState.empty());

        assertEquals(DENY, levelOf("deleteEvent"));
        assertEquals(ASK, levelOf("sendEmail"));
        assertEquals(2, service.audit().size(), "the audit log survives too");
    }

    @Test
    void theAuditLogRecordsWhoWhenWhereAndBeforeAndAfterNewestFirst() {
        service.set("calendar", "delete", "ask", CHAT, ORIGIN);
        service.set("calendar", "delete", "deny", ADMIN, ORIGIN);

        List<PermissionAuditEntry> audit = service.audit();

        assertEquals(2, audit.size());
        PermissionAuditEntry latest = audit.get(0);
        assertEquals("admin-api:qlawkus", latest.actor());
        assertEquals("rest", latest.channel());
        assertEquals("calendar.delete", latest.target());
        assertEquals("ask", latest.before());
        assertEquals("deny", latest.after());
        assertEquals(Instant.parse("2026-11-27T10:00:00Z"), latest.at());
        assertEquals("messaging:telegram:42", audit.get(1).actor());
    }

    @Test
    void theAuditLogIsBounded() {
        for (int i = 0; i < 8; i++) {
            service.set("calendar", "delete", i % 2 == 0 ? "ask" : "allow", ADMIN, ORIGIN);
        }

        assertEquals(5, service.audit().size());
    }

    @Test
    void explainNamesTheCellThatDecidedAndTheApprovalListThatAddsAsk() {
        service.set("calendar", "delete", "deny", ADMIN, ORIGIN);
        PermissionService withLegacy = new PermissionService(policy, new PermissionStateFile(root.resolve("permissions")),
                new PermissionAuditLog(root.resolve("permissions"), 5), gate,
                tool -> tool.equals("sendEmail") ? Optional.of("listed in qlawkus.approval.tools") : Optional.empty(),
                Clock.systemUTC());

        PermissionService.Explanation denied = withLegacy.explain("deleteEvent");
        PermissionService.Explanation forced = withLegacy.explain("sendEmail");

        assertEquals(DENY, denied.level());
        assertEquals("calendar", denied.group());
        assertEquals(AccessClass.DELETE, denied.accessClass());
        assertTrue(denied.reasons().get(0).contains("calendar delete"), denied.reasons().toString());
        assertEquals(ASK, forced.level());
        assertTrue(forced.reasons().stream().anyMatch(reason -> reason.contains("qlawkus.approval.tools")));
    }

    @Test
    void explainOfAnUnknownToolSaysItIsTreatedAsExecute() {
        PermissionService.Explanation explanation = service.explain("mysteryTool");

        assertEquals(AccessClass.EXECUTE, explanation.accessClass());
        assertEquals("external", explanation.group());
        assertFalse(explanation.reasons().isEmpty());
    }

    private static final class RecordingGate implements ApprovalGate {
        final List<Recorded> requests = new ArrayList<>();
        boolean refuse;

        record Recorded(String id, String action, String arguments, String preview) {
        }

        @Override
        public String requestApproval(String toolName, String rawArguments, String memoryId,
                AgentDeliveryContext.Snapshot origin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int pendingCount() {
            return requests.size();
        }

        @Override
        public Optional<String> requestOwnerAction(String action, String arguments, String preview,
                AgentDeliveryContext.Snapshot origin) {
            if (refuse) {
                return Optional.empty();
            }
            String id = String.format("%032x", requests.size() + 1);
            requests.add(new Recorded(id, action, arguments, preview));
            return Optional.of(id);
        }

        ApprovalRequest requestAsApproved(String approver, String id) {
            Recorded last = requests.get(requests.size() - 1);
            Instant now = Instant.parse("2026-11-27T10:00:00Z");
            return new ApprovalRequest(id, last.action(), last.arguments(), "hash", last.preview(), null,
                    "telegram", "42", ApprovalStatus.EXECUTING, now, now.plusSeconds(60),
                    now, approver, null);
        }
    }
}
