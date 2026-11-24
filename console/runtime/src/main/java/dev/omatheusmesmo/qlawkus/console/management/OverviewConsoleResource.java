package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.approval.ApprovalConfig;
import dev.omatheusmesmo.qlawkus.approval.ApprovalGate;
import dev.omatheusmesmo.qlawkus.compose.RedeployHistoryService;
import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.logging.FailureSummary;
import dev.omatheusmesmo.qlawkus.model.ModelChainStatus;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.task.DynamicTaskScheduler;
import dev.omatheusmesmo.qlawkus.task.TaskStatus;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.Trigger;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * The fragments behind the overview's cards. Each card is its own endpoint that HTMX polls, so one
 * slow source never holds the others back, and none of them adds an admin endpoint: every card reads
 * what the agent already exposes through its SPIs. A card whose capability is absent says so instead
 * of failing, because the bean it would read is not there to resolve.
 */
@Path("/console/overview")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class OverviewConsoleResource {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    private final Template health;
    private final Template upcoming;
    private final Template redeploy;
    private final Template memory;
    private final Template model;
    private final Template approvals;
    private final Template failed;
    private final Instance<ReadinessSource> readiness;
    private final Scheduler scheduler;
    private final DynamicTaskScheduler tasks;
    private final RedeployHistoryService redeploys;
    private final FactStore facts;
    private final EpisodicStore journals;
    private final SkillStore skills;
    private final ModelChainStatus chain;
    private final ApprovalGate gate;
    private final ApprovalConfig approvalConfig;
    private final SecretRedactor redactor;

    public OverviewConsoleResource(@Location("console/overview/health.html") Template health,
            @Location("console/overview/upcoming.html") Template upcoming,
            @Location("console/overview/redeploy.html") Template redeploy,
            @Location("console/overview/memory.html") Template memory,
            @Location("console/overview/model.html") Template model,
            @Location("console/overview/approvals.html") Template approvals,
            @Location("console/overview/error.html") Template failed,
            Instance<ReadinessSource> readiness, Scheduler scheduler, DynamicTaskScheduler tasks,
            RedeployHistoryService redeploys, FactStore facts, EpisodicStore journals, SkillStore skills,
            ModelChainStatus chain, ApprovalGate gate, ApprovalConfig approvalConfig, SecretRedactor redactor) {
        this.health = health;
        this.upcoming = upcoming;
        this.redeploy = redeploy;
        this.memory = memory;
        this.model = model;
        this.approvals = approvals;
        this.failed = failed;
        this.readiness = readiness;
        this.scheduler = scheduler;
        this.tasks = tasks;
        this.redeploys = redeploys;
        this.facts = facts;
        this.journals = journals;
        this.skills = skills;
        this.chain = chain;
        this.gate = gate;
        this.approvalConfig = approvalConfig;
        this.redactor = redactor;
    }

    @GET
    @Path("/health")
    public TemplateInstance health() {
        return card("Health", this::healthCard);
    }

    private TemplateInstance healthCard() {
        boolean available = readiness.isResolvable() && readiness.get().available();
        List<ReadinessView> checks = available ? readiness.get().views() : List.of();
        return health.data("available", available).data("checks", checks);
    }

    @GET
    @Path("/upcoming")
    public TemplateInstance upcoming() {
        return card("Upcoming runs", this::upcomingCard);
    }

    private TemplateInstance upcomingCard() {
        List<UpcomingRun> candidates = new ArrayList<>();
        for (ScheduleConsoleResource.JobDescriptor job : ScheduleConsoleResource.JOBS) {
            Trigger trigger = scheduler.getScheduledJob(job.identity());
            if (trigger != null) {
                candidates.add(run(job.label(), "job", trigger.getNextFireTime()));
            }
        }
        for (var view : tasks.list()) {
            if (view.task().status() == TaskStatus.ACTIVE && view.nextFire() != null) {
                candidates.add(run(view.task().task(), "task", view.nextFire()));
            }
        }
        return upcoming.data("runs", UpcomingRuns.next(candidates)).data("zone", tasks.zone().getId());
    }

    @GET
    @Path("/redeploy")
    public TemplateInstance redeploy() {
        return card("The last redeploy", this::redeployCard);
    }

    private TemplateInstance redeployCard() {
        RedeployRunView last = redeploys.lastRun()
                .map(run -> RedeployRunView.of(run, redeploys.history().lastSuccessAt()))
                .orElse(null);
        return redeploy.data("redeploy", last);
    }

    @GET
    @Path("/memory")
    public TemplateInstance memory() {
        return card("Memory", this::memoryCard);
    }

    private TemplateInstance memoryCard() {
        LocalDate latest = journals.listJournals().stream().map(JournalSummary::date)
                .max(Comparator.naturalOrder()).orElse(null);
        return memory.data("journals", journals.count()).data("latest", latest)
                .data("sources", facts.listSources()).data("skills", skills.index().size());
    }

    @GET
    @Path("/model")
    public TemplateInstance model() {
        return card("The model chain", () -> model.data("chain", ModelChainView.of(chain.snapshot(), tasks.zone())));
    }

    @GET
    @Path("/approvals")
    public TemplateInstance approvals() {
        return card("Approvals", this::approvalsCard);
    }

    /** Counts and tool names only: the arguments of a waiting request stay in the queue page. */
    private TemplateInstance approvalsCard() {
        List<String> gated = approvalConfig.tools().orElse(List.of());
        return approvals.data("pending", gate.pendingCount()).data("gated", gated);
    }

    /**
     * A card whose source cannot be read still answers 200, with a fragment that says so: HTMX keeps the
     * last good content when a poll fails, and a health page that goes on showing old numbers is worse
     * than one that admits it cannot read them. Only a bounded, redacted line of the reason is shown.
     */
    private TemplateInstance card(String name, Supplier<TemplateInstance> read) {
        try {
            return read.get();
        } catch (RuntimeException failure) {
            FailureSummary summary = FailureSummary.of(failure, redactor::redact);
            return failed.data("name", name).data("reason", FailureSummary.boundedLine(summary.cause(), redactor::redact));
        }
    }

    private UpcomingRun run(String label, String kind, Instant at) {
        return new UpcomingRun(label, kind, at, at == null ? null : WHEN.format(at.atZone(tasks.zone())));
    }
}
