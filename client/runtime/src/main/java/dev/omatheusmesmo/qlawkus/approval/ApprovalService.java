package dev.omatheusmesmo.qlawkus.approval;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import dev.omatheusmesmo.qlawkus.permission.PermissionLevel;
import dev.omatheusmesmo.qlawkus.permission.ToolPermissions;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

import static dev.omatheusmesmo.qlawkus.approval.ApprovalStatus.APPROVED;
import static dev.omatheusmesmo.qlawkus.approval.ApprovalStatus.DENIED;
import static dev.omatheusmesmo.qlawkus.approval.ApprovalStatus.EXECUTED;
import static dev.omatheusmesmo.qlawkus.approval.ApprovalStatus.EXECUTING;
import static dev.omatheusmesmo.qlawkus.approval.ApprovalStatus.EXPIRED;
import static dev.omatheusmesmo.qlawkus.approval.ApprovalStatus.FAILED;
import static dev.omatheusmesmo.qlawkus.approval.ApprovalStatus.NEEDS_REVIEW;
import static dev.omatheusmesmo.qlawkus.approval.ApprovalStatus.PENDING;

/**
 * Owns the lifecycle of gated tool calls: records them, decides them on the owner's word, runs an
 * approved call exactly once, and reports what happened.
 *
 * <p>The safety rests on three rules. A request is only ever moved by a compare-and-set from the status
 * the caller read, so a second callback finds the request already decided and does nothing. An approval
 * recomputes the binding hash from the stored arguments before it runs, so arguments changed after the
 * owner was asked are refused. And a request interrupted mid-execution is marked {@code NEEDS_REVIEW}
 * at boot and never replayed, because the process cannot tell whether the external effect happened.
 */
@Startup
@ApplicationScoped
public class ApprovalService implements ApprovalGate {

    static final int SUMMARY_MAX_CHARS = 200;

    private static final String REQUEST_ID_SHAPE = "ids are 32 lower-case hex characters";
    private static final String BINDING_SUMMARY = "the arguments changed after the owner was asked; not run";
    private static final String DENIED_SUMMARY = "denied by the owner; not run";
    private static final String EXPIRED_SUMMARY = "expired without a decision; not run";
    private static final String REVIEW_SUMMARY = "the process stopped before the outcome was recorded; not replayed";
    private static final String RUNNING_SUMMARY = "running";
    private static final HexFormat HEX = HexFormat.of();

    /**
     * The result of a decision. {@code request} is null only when no request has that id.
     */
    public record Decision(boolean accepted, ApprovalRequest request, String message) {
    }

    private record Settlement(ApprovalStatus status, String summary) {
    }

    private final ApprovalStore store;
    private final ApprovalConfig config;
    private final ApprovedToolExecutors tools;
    private final ApprovalNotifier notifier;
    private final UnaryOperator<String> redactor;
    private final Executor executor;
    private final AgentDeliveryContext delivery;
    private final AgentMeters meters;
    private final Clock clock;
    private final Iterable<OwnerAction> ownerActions;
    private final ToolPermissions permissions;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public ApprovalService(ApprovalStore store, ApprovalConfig config, ApprovedToolExecutors tools,
            ApprovalNotifier notifier, SecretRedactor redactor, AgentDeliveryContext delivery, AgentMeters meters,
            Instance<OwnerAction> ownerActions, ToolPermissions permissions) {
        this(store, config, tools, notifier, redactor::redact, ApprovalService::runOnWorker, delivery, meters,
                Clock.systemUTC(), ownerActions, permissions);
    }

    ApprovalService(ApprovalStore store, ApprovalConfig config, ApprovedToolExecutors tools,
            ApprovalNotifier notifier, UnaryOperator<String> redactor, Executor executor,
            AgentDeliveryContext delivery, AgentMeters meters, Clock clock, Iterable<OwnerAction> ownerActions,
            ToolPermissions permissions) {
        this.store = store;
        this.config = config;
        this.tools = tools;
        this.notifier = notifier;
        this.redactor = redactor;
        this.executor = executor;
        this.delivery = delivery;
        this.meters = meters;
        this.clock = clock;
        this.ownerActions = ownerActions;
        this.permissions = permissions;
    }

    @Override
    public String requestApproval(String toolName, String rawArguments, String memoryId,
            AgentDeliveryContext.Snapshot origin) {
        if (toolName != null && toolName.startsWith(OwnerAction.PREFIX)) {
            return refused("that name is reserved for the owner's own actions");
        }
        String canonical;
        try {
            canonical = ApprovalBinding.canonicalArguments(rawArguments);
        } catch (IllegalArgumentException e) {
            return refused("the arguments are not a JSON object");
        }
        if (!redactor.apply(canonical).equals(canonical)) {
            return refused("the arguments look like they contain a credential, and credentials are never stored "
                    + "in an approval request; reference the secret from the vault instead");
        }
        if (pendingCount() >= config.maxPending()) {
            return refused("too many requests are already waiting for the owner");
        }
        Instant now = clock.instant();
        ApprovalRequest request = new ApprovalRequest(newId(), toolName, canonical,
                ApprovalBinding.hash(toolName, canonical), preview(toolName, canonical), memoryId,
                origin.providerId(), origin.chatId(), PENDING, now, now.plus(config.expiry()), null, null, null);
        store.create(request);
        meters.approvalRequest(AgentMeters.APPROVAL_CREATED);
        Log.infof("Approval request %s created for tool %s", request.id(), toolName);
        notifier.notifyOrigin(request, ownerNotice(request));
        return modelNotice(request);
    }

    @Override
    public Optional<String> requestOwnerAction(String action, String arguments, String preview,
            AgentDeliveryContext.Snapshot origin) {
        if (action == null || !action.startsWith(OwnerAction.PREFIX)) {
            throw new IllegalArgumentException("An owner action name starts with " + OwnerAction.PREFIX);
        }
        String canonical = ApprovalBinding.canonicalArguments(arguments);
        if (pendingCount() >= config.maxPending()) {
            meters.approvalRequest(AgentMeters.APPROVAL_REFUSED);
            return Optional.empty();
        }
        Instant now = clock.instant();
        String shown = bounded(preview);
        ApprovalRequest request = new ApprovalRequest(newId(), action, canonical,
                ApprovalBinding.hash(action, canonical), shown, null, origin.providerId(), origin.chatId(), PENDING,
                now, now.plus(config.expiry()), null, null, null);
        store.create(request);
        meters.approvalRequest(AgentMeters.APPROVAL_CREATED);
        Log.infof("Approval request %s created for owner action %s", request.id(), action);
        notifier.notifyOrigin(request, ownerNotice(request));
        return Optional.of(request.id());
    }

    /**
     * Approves a pending request and runs it. The decision is recorded before the call starts, so a
     * second approval, or a denial arriving meanwhile, finds the request already decided.
     */
    public Decision approve(String id, String actor) {
        if (store.find(id).isEmpty()) {
            return notFound();
        }
        Instant now = clock.instant();
        Optional<ApprovalRequest> decided = store.transition(id, PENDING, request -> decideApproval(request, now, actor));
        if (decided.isEmpty()) {
            return alreadyDecided(id);
        }
        ApprovalRequest settled = decided.get();
        if (settled.status() == EXPIRED) {
            meters.approvalDecision(AgentMeters.DECISION_EXPIRED);
            notifier.notifyOrigin(settled, expiredNotice(settled));
            return new Decision(false, settled, "Approval request " + id + " expired; nothing was run.");
        }
        if (settled.status() == FAILED) {
            meters.approvalDecision(AgentMeters.DECISION_REFUSED);
            notifier.notifyOrigin(settled, bindingNotice(settled));
            return new Decision(false, settled, "Refused: the arguments no longer match request " + id
                    + "; nothing was run.");
        }
        Optional<ApprovalRequest> running = store.transition(id, APPROVED, request -> request.settle(EXECUTING,
                RUNNING_SUMMARY));
        if (running.isEmpty()) {
            return alreadyDecided(id);
        }
        meters.approvalDecision(AgentMeters.DECISION_APPROVED);
        Log.infof("Approval request %s approved; running tool %s", id, settled.toolName());
        ApprovalRequest started = running.get();
        executor.execute(() -> execute(started));
        return new Decision(true, started, "Approval accepted; the action is running and its outcome will follow.");
    }

    /** Refuses a pending request. A denied request can never be approved or run afterwards. */
    public Decision deny(String id, String actor) {
        if (store.find(id).isEmpty()) {
            return notFound();
        }
        Instant now = clock.instant();
        Optional<ApprovalRequest> decided = store.transition(id, PENDING, request -> isExpired(request, now)
                ? expire(request, now)
                : request.decide(DENIED, now, actor).settle(DENIED, DENIED_SUMMARY));
        if (decided.isEmpty()) {
            return alreadyDecided(id);
        }
        ApprovalRequest settled = decided.get();
        if (settled.status() == EXPIRED) {
            meters.approvalDecision(AgentMeters.DECISION_EXPIRED);
            notifier.notifyOrigin(settled, expiredNotice(settled));
            return new Decision(false, settled, "Approval request " + id + " expired; nothing was run.");
        }
        meters.approvalDecision(AgentMeters.DECISION_DENIED);
        Log.infof("Approval request %s denied", id);
        notifier.decided(settled);
        return new Decision(true, settled, "Denied; the action will not run.");
    }

    public List<ApprovalRequest> list() {
        return store.list();
    }

    public Optional<ApprovalRequest> find(String id) {
        return store.find(id);
    }

    /**
     * Marks requests that were interrupted at boot. A request left {@code EXECUTING} or {@code APPROVED}
     * was in flight when the process died, so whether its effect happened is unknown. It is never run
     * again here; it waits for the owner's review.
     */
    @PostConstruct
    void recover() {
        for (ApprovalRequest request : store.list()) {
            if (request.status() != EXECUTING && request.status() != APPROVED) {
                continue;
            }
            store.transition(request.id(), request.status(), interrupted -> interrupted.settle(NEEDS_REVIEW,
                    REVIEW_SUMMARY)).ifPresent(review -> {
                meters.approvalNeedsReview();
                Log.warnf("Approval request %s was interrupted while %s; marked needs-review and not replayed",
                        review.id(), request.status());
                notifier.notifyOrigin(review, reviewNotice(review));
            });
        }
    }

    @Scheduled(identity = "approval-expiry", every = "60s", concurrentExecution = ConcurrentExecution.SKIP)
    public void sweepExpired() {
        Instant now = clock.instant();
        for (ApprovalRequest request : store.list()) {
            if (request.status() == PENDING && isExpired(request, now)) {
                store.transition(request.id(), PENDING, pending -> expire(pending, now)).ifPresent(expired -> {
                    meters.approvalDecision(AgentMeters.DECISION_EXPIRED);
                    notifier.notifyOrigin(expired, expiredNotice(expired));
                });
            }
        }
    }

    private void execute(ApprovalRequest running) {
        Settlement settlement = runTool(running);
        ApprovalRequest settled = store.transition(running.id(), EXECUTING,
                request -> request.settle(settlement.status(), settlement.summary()))
                .orElseGet(() -> {
                    Log.warnf("Approval request %s left EXECUTING by someone else; outcome not recorded", running.id());
                    return running;
                });
        meters.approvalExecution(settlement.status() == EXECUTED
                ? AgentMeters.EXECUTION_EXECUTED
                : AgentMeters.EXECUTION_FAILED);
        Log.infof("Approval request %s finished with status %s", running.id(), settlement.status());
        notifier.decided(settled);
    }

    private Settlement runTool(ApprovalRequest running) {
        AgentDeliveryContext.Snapshot previous = delivery.snapshot();
        delivery.set(running.memoryId(), running.providerId(), running.chatId());
        try {
            Optional<OwnerAction> action = ownerAction(running.toolName());
            if (running.isOwnerAction()) {
                if (action.isEmpty()) {
                    return new Settlement(FAILED, "no handler for this action; not run");
                }
                return new Settlement(EXECUTED, bounded(redactor.apply(action.get().run(running))));
            }
            if (permissions.levelOf(running.toolName()) == PermissionLevel.DENY) {
                return new Settlement(FAILED, "denied by the permission policy since it was asked; not run");
            }
            Optional<ToolExecutor> executorOfTool = tools.unguardedExecutor(running.toolName());
            if (executorOfTool.isEmpty()) {
                return new Settlement(FAILED, "the tool is not registered; not run");
            }
            ToolExecutionRequest call = ToolExecutionRequest.builder()
                    .name(running.toolName())
                    .arguments(running.arguments())
                    .build();
            String result = ApprovedExecution.run(() -> executorOfTool.get().execute(call, running.memoryId()));
            return new Settlement(EXECUTED, bounded(redactor.apply(result)));
        } catch (RuntimeException e) {
            return new Settlement(FAILED,
                    bounded(redactor.apply(e.getClass().getSimpleName() + ": " + e.getMessage())));
        } finally {
            delivery.restore(previous);
        }
    }

    private Optional<OwnerAction> ownerAction(String name) {
        for (OwnerAction action : ownerActions) {
            if (action.name().equals(name)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }

    private ApprovalRequest decideApproval(ApprovalRequest request, Instant now, String actor) {
        if (isExpired(request, now)) {
            return expire(request, now);
        }
        if (!ApprovalBinding.hash(request.toolName(), request.arguments()).equals(request.bindingHash())) {
            return request.decide(FAILED, now, actor).settle(FAILED, BINDING_SUMMARY);
        }
        return request.decide(APPROVED, now, actor);
    }

    private static ApprovalRequest expire(ApprovalRequest request, Instant now) {
        return request.decide(EXPIRED, now, null).settle(EXPIRED, EXPIRED_SUMMARY);
    }

    private static boolean isExpired(ApprovalRequest request, Instant now) {
        return !now.isBefore(request.expiresAt());
    }

    @Override
    public int pendingCount() {
        return (int) store.list().stream().filter(request -> request.status() == PENDING).count();
    }

    private String preview(String toolName, String canonical) {
        int max = config.previewMaxChars();
        String shown = canonical.length() <= max ? canonical : canonical.substring(0, max) + "...";
        return toolName + " " + shown;
    }

    private String newId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    private String refused(String reason) {
        meters.approvalRequest(AgentMeters.APPROVAL_REFUSED);
        return "Not run: " + reason + ". No approval request was created.";
    }

    private String modelNotice(ApprovalRequest request) {
        return "Not run: the tool " + request.toolName() + " needs owner approval. Approval request "
                + request.id() + " is pending until " + request.expiresAt() + ". The action has NOT been executed. "
                + "Do not retry it and do not ask for confirmation in chat: the owner decides outside this "
                + "conversation, and the outcome is passed back to you when this task resumes.";
    }

    private static String ownerNotice(ApprovalRequest request) {
        if (request.isOwnerAction()) {
            return "Approval needed: " + request.preview() + " (request " + request.id() + ").\n"
                    + "Reply /approve " + request.id() + " to apply it, or /deny " + request.id() + " to refuse. "
                    + "It expires at " + request.expiresAt() + ".";
        }
        return "Approval needed for " + request.toolName() + " (request " + request.id() + ").\n"
                + "Preview: " + request.preview() + "\n"
                + "Reply /approve " + request.id() + " to run it, or /deny " + request.id() + " to refuse. "
                + "It expires at " + request.expiresAt() + ".";
    }

    private static String expiredNotice(ApprovalRequest request) {
        return "Approval request " + request.id() + " for " + request.toolName()
                + " expired without a decision; the action did not run.";
    }

    private static String bindingNotice(ApprovalRequest request) {
        return "Approval request " + request.id() + " for " + request.toolName()
                + " was refused: its arguments changed after the owner was asked, so the action did not run.";
    }

    private static String reviewNotice(ApprovalRequest request) {
        return "Approval request " + request.id() + " for " + request.toolName()
                + " was interrupted while it ran. It is not known whether the action completed, so it was not "
                + "replayed; the owner needs to review it before anything else is done.";
    }

    private Decision notFound() {
        return new Decision(false, null, "Approval request not found.");
    }

    private Decision alreadyDecided(String id) {
        return store.find(id)
                .map(request -> new Decision(false, request, "Approval request is already "
                        + request.status().name().toLowerCase(Locale.ROOT) + "; nothing was run."))
                .orElseGet(this::notFound);
    }

    private static String bounded(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= SUMMARY_MAX_CHARS ? text : text.substring(0, SUMMARY_MAX_CHARS - 3) + "...";
    }

    private static void runOnWorker(Runnable task) {
        Infrastructure.getDefaultWorkerPool().execute(() -> inRequestContext(task));
    }

    private static void inRequestContext(Runnable task) {
        ManagedContext requestContext = Arc.container().requestContext();
        boolean activated = false;
        if (!requestContext.isActive()) {
            requestContext.activate();
            activated = true;
        }
        try {
            task.run();
        } finally {
            if (activated) {
                requestContext.terminate();
            }
        }
    }
}
