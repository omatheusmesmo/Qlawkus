package dev.omatheusmesmo.qlawkus.compose;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.dto.RedeployHistory;
import dev.omatheusmesmo.qlawkus.dto.RedeployPhaseTotal;
import dev.omatheusmesmo.qlawkus.dto.RedeployReport;
import dev.omatheusmesmo.qlawkus.dto.RedeployRun;
import dev.omatheusmesmo.qlawkus.dto.RedeployTotal;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Records what the external redeploy loop reports about each run, and is the only source the
 * redeploy meters read from.
 *
 * <p>The loop's phase 4 restarts the process that receives its report, so an in-memory meter would
 * restart at zero exactly when a successful redeploy increments it. The history file under
 * {@code state.root} is what survives, so the totals and the last success are kept there and the
 * meters are functions over it: a restarted instance publishes the totals its predecessor reached.
 *
 * <p>This records data only. A report is validated at the API boundary and nothing in it is ever
 * executed, so a leaked admin credential can at most write a misleading entry into a bounded file.
 */
@Startup
@ApplicationScoped
public class RedeployHistoryService {

    static final String HISTORY_FILE = "redeploys.json";
    static final int MAX_RUNS = 20;

    private static final String NO_PHASE = "none";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final RedeployHistory EMPTY = new RedeployHistory(null, List.of(), List.of(), List.of());

    private final Path file;
    private final AgentMeters meters;
    private final Clock clock;

    private volatile RedeployHistory history;

    @Inject
    public RedeployHistoryService(AgentConfig config, AgentMeters meters) {
        this(Path.of(config.state().root(), HISTORY_FILE), meters, Clock.systemUTC());
    }

    RedeployHistoryService(Path file, AgentMeters meters, Clock clock) {
        this.file = file;
        this.meters = meters;
        this.clock = clock;
        this.history = load(file);
    }

    /**
     * Publishes every redeploy series from boot, at zero where nothing has happened yet, so the
     * first increment after a restart is a visible change rather than a series appearing at one.
     */
    @PostConstruct
    void publishMeters() {
        meters.redeployRuns(RedeployReport.SUCCESS, NO_PHASE, this,
                service -> service.runCount(RedeployReport.SUCCESS, NO_PHASE));
        for (String phase : RedeployReport.PHASE_ORDER) {
            meters.redeployRuns(RedeployReport.FAILURE, phase, this,
                    service -> service.runCount(RedeployReport.FAILURE, phase));
            for (String outcome : List.of(RedeployReport.SUCCESS, RedeployReport.FAILURE)) {
                meters.redeployPhase(phase, outcome, this,
                        service -> service.phaseTotal(phase, outcome).count(),
                        service -> service.phaseTotal(phase, outcome).totalMillis());
            }
        }
        meters.redeployLastSuccessAge(this, RedeployHistoryService::secondsSinceLastSuccess);
    }

    /**
     * Records one run and persists the whole history before returning, so an acknowledged report is
     * already on disk when the loop moves on.
     */
    public synchronized RedeployRun record(RedeployReport report) throws IOException {
        Instant now = clock.instant();
        RedeployRun run = new RedeployRun(now.toString(), report.startedAt().toString(), report.outcome(),
                report.failedPhase(), inContractOrder(report.phases()), report.message());

        List<RedeployRun> runs = new ArrayList<>(MAX_RUNS);
        runs.add(run);
        history.runs().stream().limit(MAX_RUNS - 1L).forEach(runs::add);

        RedeployHistory next = new RedeployHistory(
                RedeployReport.SUCCESS.equals(run.outcome()) ? run.recordedAt() : history.lastSuccessAt(),
                List.copyOf(runs),
                countRun(history.totals(), run),
                addPhases(history.phaseTotals(), run));
        write(next);
        history = next;
        return run;
    }

    public RedeployHistory history() {
        return history;
    }

    public Optional<RedeployRun> lastRun() {
        return history.runs().stream().findFirst();
    }

    private double runCount(String outcome, String failedPhase) {
        return history.totals().stream()
                .filter(total -> total.outcome().equals(outcome) && total.failedPhase().equals(failedPhase))
                .mapToLong(RedeployTotal::count)
                .sum();
    }

    private RedeployPhaseTotal phaseTotal(String phase, String outcome) {
        return history.phaseTotals().stream()
                .filter(total -> total.phase().equals(phase) && total.outcome().equals(outcome))
                .findFirst()
                .orElse(new RedeployPhaseTotal(phase, outcome, 0, 0));
    }

    private double secondsSinceLastSuccess() {
        String lastSuccessAt = history.lastSuccessAt();
        if (lastSuccessAt == null) {
            return Double.NaN;
        }
        return Duration.between(Instant.parse(lastSuccessAt), clock.instant()).toMillis() / 1000.0;
    }

    private static Map<String, Long> inContractOrder(Map<String, Long> phases) {
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (String phase : RedeployReport.PHASE_ORDER) {
            if (phases.containsKey(phase)) {
                ordered.put(phase, phases.get(phase));
            }
        }
        return ordered;
    }

    private static List<RedeployTotal> countRun(List<RedeployTotal> totals, RedeployRun run) {
        String failedPhase = run.failedPhase() == null ? NO_PHASE : run.failedPhase();
        Predicate<RedeployTotal> matches = total ->
                total.outcome().equals(run.outcome()) && total.failedPhase().equals(failedPhase);
        List<RedeployTotal> next = new ArrayList<>(totals.stream().filter(matches.negate()).toList());
        long previous = totals.stream().filter(matches).mapToLong(RedeployTotal::count).sum();
        next.add(new RedeployTotal(run.outcome(), failedPhase, previous + 1));
        return List.copyOf(next);
    }

    private static List<RedeployPhaseTotal> addPhases(List<RedeployPhaseTotal> totals, RedeployRun run) {
        List<RedeployPhaseTotal> next = new ArrayList<>(totals);
        run.phases().forEach((phase, millis) -> {
            String outcome = phase.equals(run.failedPhase()) ? RedeployReport.FAILURE : RedeployReport.SUCCESS;
            Predicate<RedeployPhaseTotal> matches = total ->
                    total.phase().equals(phase) && total.outcome().equals(outcome);
            RedeployPhaseTotal previous = next.stream().filter(matches)
                    .findFirst()
                    .orElse(new RedeployPhaseTotal(phase, outcome, 0, 0));
            next.removeIf(matches);
            next.add(new RedeployPhaseTotal(phase, outcome, previous.count() + 1,
                    previous.totalMillis() + millis));
        });
        return List.copyOf(next);
    }

    /**
     * Writes to a sibling file and moves it into place, so a crash mid-write leaves the previous
     * history intact instead of a truncated file that loses every total.
     */
    private void write(RedeployHistory next) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, JSON.writeValueAsString(next), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Reads the history, treating an unreadable file as empty. Redeploy history is observability: a
     * corrupt file must cost its contents, never the agent's boot.
     */
    private static RedeployHistory load(Path file) {
        if (!Files.exists(file)) {
            return EMPTY;
        }
        try {
            RedeployHistory loaded = JSON.readValue(file.toFile(), RedeployHistory.class);
            return new RedeployHistory(loaded.lastSuccessAt(),
                    loaded.runs() == null ? List.of() : List.copyOf(loaded.runs()),
                    loaded.totals() == null ? List.of() : List.copyOf(loaded.totals()),
                    loaded.phaseTotals() == null ? List.of() : List.copyOf(loaded.phaseTotals()));
        } catch (IOException | RuntimeException e) {
            Log.warnf(e, "Redeploy history at %s is unreadable; starting a new one", file);
            return EMPTY;
        }
    }
}
