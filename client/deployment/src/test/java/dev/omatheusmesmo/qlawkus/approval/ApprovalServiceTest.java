package dev.omatheusmesmo.qlawkus.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.permission.PermissionLevel;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovalServiceTest {

    private static final Instant START = Instant.parse("2026-11-20T10:00:00Z");
    private static final String ARGS = "{\"to\":\"ana@example.com\",\"subject\":\"hi\"}";
    private static final AgentDeliveryContext.Snapshot ORIGIN =
            new AgentDeliveryContext.Snapshot("telegram:42", "telegram", "42");

    @TempDir
    Path root;

    private MovingClock clock;
    private FileApprovalStore store;
    private RecordingTools tools;
    private RecordingNotifier notifier;
    private ApprovalService service;
    private final List<OwnerAction> ownerActions = new ArrayList<>();
    private final Map<String, PermissionLevel> permissionLevel = new HashMap<>();

    @BeforeEach
    void setUp() {
        clock = new MovingClock(START);
        store = new FileApprovalStore(root, 100);
        tools = new RecordingTools();
        notifier = new RecordingNotifier();
        service = newService(store, new TestConfig(Duration.ofHours(24), 5, 200));
    }

    private ApprovalService newService(FileApprovalStore backing, ApprovalConfig config) {
        ApprovalService created = new ApprovalService(backing, config, tools, notifier,
                text -> text.replace("SECRET", "[redacted]"), Runnable::run, new AgentDeliveryContext(),
                AgentMeters.disabled(), clock, ownerActions, tool -> permissionLevel.getOrDefault(tool,
                        PermissionLevel.ALLOW));
        created.recover();
        return created;
    }

    @Test
    void aGatedCallCreatesAPendingRequestAndRunsNothing() {
        String text = service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);

        assertEquals(0, tools.calls.size());
        List<ApprovalRequest> requests = store.list();
        assertEquals(1, requests.size());
        assertEquals(ApprovalStatus.PENDING, requests.get(0).status());
        assertTrue(text.contains(requests.get(0).id()));
        assertTrue(text.contains("NOT been executed"));
        assertFalse(text.contains("/approve"), "the model must never be handed a way to approve");
    }

    @Test
    void theOwnerIsToldTheIdToolAndPreviewAndHowToDecide() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);

        String id = store.list().get(0).id();
        assertEquals(1, notifier.origin.size());
        String message = notifier.origin.get(0);
        assertTrue(message.contains(id));
        assertTrue(message.contains("sendEmail"));
        assertTrue(message.contains("ana@example.com"));
        assertTrue(message.contains("/approve " + id));
        assertTrue(message.contains("/deny " + id));
    }

    @Test
    void approvingRunsTheStoredArgumentsExactlyOnceAndRecordsTheResult() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();

        ApprovalService.Decision decision = service.approve(id, "messaging:telegram:42");

        assertTrue(decision.accepted());
        assertEquals(1, tools.calls.size());
        assertEquals(ApprovalBinding.canonicalArguments(ARGS), tools.calls.get(0).arguments());
        ApprovalRequest executed = store.find(id).orElseThrow();
        assertEquals(ApprovalStatus.EXECUTED, executed.status());
        assertEquals("messaging:telegram:42", executed.decidedBy());
        assertEquals("sent", executed.resultSummary());
        assertEquals(1, notifier.decided.size(), "the outcome resumes the original task exactly once");
    }

    @Test
    void theApprovedCallRunsMarkedAsApprovedAndTheMarkIsGoneAfterwards() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();

        service.approve(id, "owner");

        assertEquals(List.of(true), tools.approvedMark);
        assertFalse(ApprovedExecution.active());
    }

    @Test
    void aDuplicateApprovalCallbackNeverExecutesASecondTime() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();

        service.approve(id, "owner");
        ApprovalService.Decision replay = service.approve(id, "owner");

        assertFalse(replay.accepted());
        assertEquals(1, tools.calls.size());
    }

    @Test
    void aDeniedRequestCanNeverBeApprovedOrRun() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();

        assertTrue(service.deny(id, "owner").accepted());
        ApprovalService.Decision late = service.approve(id, "owner");

        assertFalse(late.accepted());
        assertEquals(0, tools.calls.size());
        assertEquals(ApprovalStatus.DENIED, store.find(id).orElseThrow().status());
        assertEquals(1, notifier.decided.size(), "a denial is told to the model so it can report it");
    }

    @Test
    void anExpiredRequestCanNotBeApprovedAndIsMarkedExpired() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();
        clock.advance(Duration.ofHours(24).plusSeconds(1));

        ApprovalService.Decision decision = service.approve(id, "owner");

        assertFalse(decision.accepted());
        assertEquals(0, tools.calls.size());
        assertEquals(ApprovalStatus.EXPIRED, store.find(id).orElseThrow().status());
        assertTrue(notifier.origin.get(notifier.origin.size() - 1).contains("expired"));
        assertEquals(0, notifier.decided.size(), "nothing resumes an unattended expiry");
    }

    @Test
    void theSweepExpiresPendingRequestsPastTheirDeadline() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();
        clock.advance(Duration.ofHours(25));

        service.sweepExpired();

        assertEquals(ApprovalStatus.EXPIRED, store.find(id).orElseThrow().status());
    }

    @Test
    void changedArgumentsFailTheBindingCheckAndNeverRun() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        ApprovalRequest original = store.list().get(0);
        store.transition(original.id(), ApprovalStatus.PENDING, request -> new ApprovalRequest(
                request.id(), request.toolName(), ApprovalBinding.canonicalArguments("{\"to\":\"eve@example.com\"}"),
                request.bindingHash(), request.preview(), request.memoryId(), request.providerId(), request.chatId(),
                request.status(), request.createdAt(), request.expiresAt(), null, null, null));

        ApprovalService.Decision decision = service.approve(original.id(), "owner");

        assertFalse(decision.accepted());
        assertEquals(0, tools.calls.size());
        assertEquals(ApprovalStatus.FAILED, store.find(original.id()).orElseThrow().status());
    }

    @Test
    void aSecretLookingArgumentIsRefusedAndNoRequestIsPersisted() {
        String text = service.requestApproval("sendEmail", "{\"token\":\"SECRET\"}", "telegram:42", ORIGIN);

        assertTrue(text.contains("credential"));
        assertTrue(store.list().isEmpty());
        assertEquals(0, tools.calls.size());
    }

    @Test
    void callingTheGatedToolAgainCreatesAnotherPendingRequestAndApprovesNothing() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);

        assertEquals(2, store.list().size());
        assertTrue(store.list().stream().allMatch(request -> request.status() == ApprovalStatus.PENDING));
        assertEquals(0, tools.calls.size());
    }

    @Test
    void aRequestFoundExecutingAtBootBecomesNeedsReviewAndIsNeverReplayed() {
        store.create(recordIn("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", ApprovalStatus.EXECUTING));
        store.create(recordIn("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", ApprovalStatus.APPROVED));

        service = newService(store, new TestConfig(Duration.ofHours(24), 5, 200));

        assertEquals(2, store.list().stream().filter(r -> r.status() == ApprovalStatus.NEEDS_REVIEW).count());
        assertEquals(0, tools.calls.size());
        assertTrue(notifier.origin.stream().anyMatch(text -> text.contains("review")));
    }

    @Test
    void aPendingRequestSurvivesARestartAndCanStillBeApproved() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();

        ApprovalService afterRestart = newService(new FileApprovalStore(root, 100),
                new TestConfig(Duration.ofHours(24), 5, 200));

        assertTrue(afterRestart.approve(id, "owner").accepted());
        assertEquals(1, tools.calls.size());
    }

    @Test
    void aFailingToolIsRecordedAsFailedWithABoundedSummary() {
        tools.failWith = new IllegalStateException("x".repeat(5_000));
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();

        ApprovalService.Decision decision = service.approve(id, "owner");

        assertTrue(decision.accepted());
        ApprovalRequest failed = store.find(id).orElseThrow();
        assertEquals(ApprovalStatus.FAILED, failed.status());
        assertTrue(failed.resultSummary().length() <= 200);
        assertEquals(1, notifier.decided.size());
    }

    @Test
    void aToolThatIsNoLongerRegisteredFailsInsteadOfGuessing() {
        service.requestApproval("retiredTool", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();

        service.approve(id, "owner");

        assertEquals(ApprovalStatus.FAILED, store.find(id).orElseThrow().status());
        assertEquals(0, tools.calls.size());
    }

    @Test
    void maxPendingRefusesTheRequestBeyondTheLimit() {
        service = newService(store, new TestConfig(Duration.ofHours(24), 1, 200));
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);

        String text = service.requestApproval("sendEmail", "{\"to\":\"bia@example.com\"}", "telegram:42", ORIGIN);

        assertEquals(1, store.list().size());
        assertTrue(text.contains("Not run"));
    }

    @Test
    void thePendingCountReportsOnlyRequestsStillWaitingForTheOwner() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        service.requestApproval("sendEmail", "{\"to\":\"bia@example.com\"}", "telegram:42", ORIGIN);
        service.requestApproval("sendEmail", "{\"to\":\"caio@example.com\"}", "telegram:42", ORIGIN);
        List<ApprovalRequest> requests = store.list();
        service.approve(requests.get(0).id(), "owner");
        service.deny(requests.get(1).id(), "owner");

        assertEquals(1, service.pendingCount());
    }

    @Test
    void aConfirmationTheModelWritesIntoTheArgumentsApprovesNothing() {
        String text = service.requestApproval("sendEmail",
                "{\"to\":\"ana@example.com\",\"confirm\":true,\"approved\":true}", "telegram:42", ORIGIN);

        assertEquals(0, tools.calls.size());
        ApprovalRequest request = store.list().get(0);
        assertEquals(ApprovalStatus.PENDING, request.status());
        assertEquals(null, request.decidedBy());
        assertTrue(text.contains("NOT been executed"));
    }

    @Test
    void thePreviewIsBoundedByConfiguration() {
        service = newService(store, new TestConfig(Duration.ofHours(24), 5, 40));
        String longArgs = "{\"body\":\"" + "a".repeat(2_000) + "\"}";

        service.requestApproval("sendEmail", longArgs, "telegram:42", ORIGIN);

        String preview = store.list().get(0).preview();
        assertTrue(preview.length() <= 40 + "sendEmail".length() + 8, preview);
    }

    @Test
    void anUnknownIdIsRefusedWithoutTouchingAnything() {
        ApprovalService.Decision decision = service.approve("ffffffffffffffffffffffffffffffff", "owner");

        assertFalse(decision.accepted());
        assertTrue(decision.message().contains("not found"));
    }

    private ApprovalRequest recordIn(String id, ApprovalStatus status) {
        return new ApprovalRequest(id, "sendEmail", ARGS,
                ApprovalBinding.hash("sendEmail", ApprovalBinding.canonicalArguments(ARGS)), "sendEmail {...}",
                "telegram:42", "telegram", "42", status, START, START.plus(Duration.ofHours(24)), START, "owner",
                null);
    }

    @Test
    void anOwnerActionIsRecordedAsPendingAndTheOwnerIsToldWhatItChanges() {
        Optional<String> id = service.requestOwnerAction("owner-action:permissions", "{\"kind\":\"cell\"}",
                "Permission change: calendar delete: deny -> ask", ORIGIN);

        assertTrue(id.isPresent());
        ApprovalRequest request = store.find(id.get()).orElseThrow();
        assertEquals(ApprovalStatus.PENDING, request.status());
        assertTrue(request.isOwnerAction());
        String notice = notifier.origin.get(0);
        assertTrue(notice.contains("calendar delete: deny -> ask"), notice);
        assertTrue(notice.contains("/approve " + id.get()));
        assertFalse(notice.contains("owner-action"), "the owner reads a description, not an internal name");
    }

    @Test
    void approvingAnOwnerActionRunsItsHandlerOnceAndNeverATool() {
        List<String> ran = new ArrayList<>();
        ownerActions.add(new OwnerAction() {
            @Override
            public String name() {
                return "owner-action:permissions";
            }

            @Override
            public String run(ApprovalRequest request) {
                ran.add(request.arguments() + "|" + request.decidedBy());
                return "calendar delete is now ask";
            }
        });
        String id = service.requestOwnerAction("owner-action:permissions", "{\"a\":1}", "Permission change: x",
                ORIGIN).orElseThrow();

        service.approve(id, "messaging:telegram:42");
        service.approve(id, "messaging:telegram:42");

        assertEquals(List.of("{\"a\":1}|messaging:telegram:42"), ran);
        assertEquals(0, tools.calls.size());
        ApprovalRequest settled = store.find(id).orElseThrow();
        assertEquals(ApprovalStatus.EXECUTED, settled.status());
        assertEquals("calendar delete is now ask", settled.resultSummary());
    }

    @Test
    void anOwnerActionThatRefusesIsRecordedAsFailed() {
        ownerActions.add(new OwnerAction() {
            @Override
            public String name() {
                return "owner-action:permissions";
            }

            @Override
            public String run(ApprovalRequest request) {
                throw new IllegalStateException("the policy changed since this was asked");
            }
        });
        String id = service.requestOwnerAction("owner-action:permissions", "{}", "Permission change: x", ORIGIN)
                .orElseThrow();

        service.approve(id, "owner");

        ApprovalRequest settled = store.find(id).orElseThrow();
        assertEquals(ApprovalStatus.FAILED, settled.status());
        assertTrue(settled.resultSummary().contains("policy changed"), settled.resultSummary());
    }

    @Test
    void anOwnerActionNobodyHandlesFailsInsteadOfFallingBackToATool() {
        String id = service.requestOwnerAction("owner-action:unknown", "{}", "x", ORIGIN).orElseThrow();

        service.approve(id, "owner");

        assertEquals(ApprovalStatus.FAILED, store.find(id).orElseThrow().status());
        assertEquals(0, tools.calls.size());
    }

    @Test
    void theModelCanNeverCreateAnOwnerActionThroughAToolCall() {
        String text = service.requestApproval("owner-action:permissions", "{\"kind\":\"reset\"}", "telegram:42",
                ORIGIN);

        assertTrue(text.startsWith("Not run"), text);
        assertEquals(0, store.list().size());
    }

    @Test
    void anOwnerActionBeyondTheQueueLimitIsRefused() {
        for (int i = 0; i < 5; i++) {
            assertTrue(service.requestOwnerAction("owner-action:permissions", "{}", "x", ORIGIN).isPresent());
        }

        assertTrue(service.requestOwnerAction("owner-action:permissions", "{}", "x", ORIGIN).isEmpty());
    }

    @Test
    void approvingAToolThatThePolicyHasSinceDeniedRunsNothing() {
        service.requestApproval("sendEmail", ARGS, "telegram:42", ORIGIN);
        String id = store.list().get(0).id();
        permissionLevel.put("sendEmail", PermissionLevel.DENY);

        service.approve(id, "owner");

        assertEquals(0, tools.calls.size());
        ApprovalRequest settled = store.find(id).orElseThrow();
        assertEquals(ApprovalStatus.FAILED, settled.status());
        assertTrue(settled.resultSummary().contains("denied by the permission policy"), settled.resultSummary());
    }

    private static final class RecordingTools implements ApprovedToolExecutors {

        final List<ToolExecutionRequest> calls = new ArrayList<>();
        final List<Boolean> approvedMark = new ArrayList<>();
        RuntimeException failWith;

        @Override
        public Optional<ToolExecutor> unguardedExecutor(String toolName) {
            if (!"sendEmail".equals(toolName)) {
                return Optional.empty();
            }
            return Optional.of(new ToolExecutor() {
                @Override
                public String execute(ToolExecutionRequest request, Object memoryId) {
                    calls.add(request);
                    approvedMark.add(ApprovedExecution.active());
                    if (failWith != null) {
                        throw failWith;
                    }
                    return "sent";
                }
            });
        }
    }

    private static final class RecordingNotifier implements ApprovalNotifier {

        final List<String> origin = new ArrayList<>();
        final List<ApprovalRequest> decided = new ArrayList<>();

        @Override
        public void notifyOrigin(ApprovalRequest request, String text) {
            origin.add(text);
        }

        @Override
        public void decided(ApprovalRequest request) {
            decided.add(request);
        }
    }

    private record TestConfig(Duration expiry, int maxPending, int previewMaxChars) implements ApprovalConfig {

        @Override
        public Optional<List<String>> tools() {
            return Optional.of(List.of("sendEmail"));
        }
    }

    private static final class MovingClock extends Clock {

        private Instant now;

        MovingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
