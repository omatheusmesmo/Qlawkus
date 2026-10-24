package dev.omatheusmesmo.qlawkus.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.dto.RedeployHistory;
import dev.omatheusmesmo.qlawkus.dto.RedeployReport;
import dev.omatheusmesmo.qlawkus.dto.RedeployRun;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The redeploy history is the only thing about a redeploy that outlives it: phase 4 restarts the
 * very process that receives the report, so whatever the meters say after a restart has to be read
 * back from disk. Every test here that matters builds a second service on the same file, which is
 * what the restarted instance does.
 */
class RedeployHistoryServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");

    private final MutableClock clock = new MutableClock(T0);

    @Test
    void aRecordedRunSurvivesANewInstance(@TempDir Path dir) throws IOException {
        serviceIn(dir).record(failure("build", "docker build exited 3"));

        RedeployHistory history = serviceIn(dir).history();

        assertEquals(1, history.runs().size(), "the restarted instance must see the run");
        RedeployRun run = history.runs().get(0);
        assertEquals("failure", run.outcome());
        assertEquals("build", run.failedPhase());
        assertEquals("docker build exited 3", run.message());
        assertEquals(T0.toString(), run.recordedAt(), "recordedAt is the app's clock, not the loop's");
        assertEquals(List.of("fetch", "promote", "build"), List.copyOf(run.phases().keySet()),
                "phases come back in contract order");
    }

    @Test
    void runsAreNewestFirstAndBounded(@TempDir Path dir) throws IOException {
        RedeployHistoryService service = serviceIn(dir);
        for (int i = 0; i < RedeployHistoryService.MAX_RUNS + 5; i++) {
            clock.advance(Duration.ofMinutes(1));
            service.record(failure("verify", "run " + i));
        }

        List<RedeployRun> runs = serviceIn(dir).history().runs();

        assertEquals(RedeployHistoryService.MAX_RUNS, runs.size(), "the history is bounded");
        assertEquals("run " + (RedeployHistoryService.MAX_RUNS + 4), runs.get(0).message(),
                "the newest run comes first");
    }

    /**
     * The gauge answers "how long since a redeploy last worked", which is the question a streak of
     * failures makes urgent. If the last success lived only in the bounded run list, that same
     * streak would push it out and the gauge would go blank exactly when it is needed.
     */
    @Test
    void theLastSuccessOutlivesAStreakOfFailures(@TempDir Path dir) throws IOException {
        RedeployHistoryService service = serviceIn(dir);
        service.record(success());
        for (int i = 0; i < RedeployHistoryService.MAX_RUNS + 1; i++) {
            clock.advance(Duration.ofMinutes(1));
            service.record(failure("build", null));
        }

        RedeployHistory history = serviceIn(dir).history();

        assertTrue(history.runs().stream().noneMatch(run -> "success".equals(run.outcome())));
        assertEquals(T0.toString(), history.lastSuccessAt());
    }

    @Test
    void totalsCountEveryRunNotOnlyTheRetainedOnes(@TempDir Path dir) throws IOException {
        RedeployHistoryService service = serviceIn(dir);
        for (int i = 0; i < RedeployHistoryService.MAX_RUNS + 3; i++) {
            service.record(failure("build", null));
        }
        service.record(success());

        RedeployHistory history = serviceIn(dir).history();

        assertEquals(RedeployHistoryService.MAX_RUNS + 3, total(history, "failure", "build"));
        assertEquals(1, total(history, "success", "none"));
    }

    @Test
    void anEmptyHistoryHasNoRunAndNoSuccess(@TempDir Path dir) {
        RedeployHistoryService service = serviceIn(dir);

        assertTrue(service.lastRun().isEmpty());
        assertNull(service.history().lastSuccessAt());
        assertFalse(Files.exists(dir.resolve(RedeployHistoryService.HISTORY_FILE)),
                "reading never creates the file");
    }

    @Test
    void aCorruptFileDoesNotStopTheAgent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(RedeployHistoryService.HISTORY_FILE), "{ not json");

        RedeployHistoryService service = serviceIn(dir);
        service.record(success());

        assertEquals(1, serviceIn(dir).history().runs().size(), "a fresh history replaces the corrupt one");
    }

    @Test
    void writesLeaveNoTemporaryFileBehind(@TempDir Path dir) throws IOException {
        serviceIn(dir).record(success());

        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(RedeployHistoryService.HISTORY_FILE),
                    files.map(file -> file.getFileName().toString()).toList());
        }
    }

    /**
     * The point of reading the meters from the file: the counter a restarted instance publishes must
     * continue from the total the previous process reached. An in-memory counter would restart at
     * zero after every successful redeploy, the one moment it is incremented.
     */
    @Test
    void theRunCounterContinuesAcrossARestart(@TempDir Path dir) throws IOException {
        serviceIn(dir).record(failure("build", null));
        serviceIn(dir).record(failure("build", null));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedeployHistoryService restarted = metered(dir, registry);
        restarted.record(success());

        assertEquals(2.0, registry.get(AgentMeters.REDEPLOY_RUNS)
                .tags("outcome", "failure", "failed_phase", "build").functionCounter().count());
        assertEquals(1.0, registry.get(AgentMeters.REDEPLOY_RUNS)
                .tags("outcome", "success", "failed_phase", "none").functionCounter().count());
    }

    /** Every series exists from boot, at zero, so the first increment after a restart is visible. */
    @Test
    void everyRunSeriesIsPublishedAtZeroBeforeAnyRun(@TempDir Path dir) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        metered(dir, registry);

        assertEquals(0.0, registry.get(AgentMeters.REDEPLOY_RUNS)
                .tags("outcome", "failure", "failed_phase", "verify").functionCounter().count());
        assertEquals(0L, registry.get(AgentMeters.REDEPLOY_PHASE_DURATION)
                .tags("phase", "restart", "outcome", "success").functionTimer().count());
    }

    /**
     * The failed phase is timed as a failure and the phases before it as successes, so a slow build
     * and a build that failed slowly are different series.
     */
    @Test
    void phaseDurationsAreSplitByWhetherThatPhaseFailed(@TempDir Path dir) throws IOException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedeployHistoryService service = metered(dir, registry);

        service.record(failure("build", null));

        FunctionTimer failedBuild = registry.get(AgentMeters.REDEPLOY_PHASE_DURATION)
                .tags("phase", "build", "outcome", "failure").functionTimer();
        FunctionTimer fetch = registry.get(AgentMeters.REDEPLOY_PHASE_DURATION)
                .tags("phase", "fetch", "outcome", "success").functionTimer();
        assertEquals(1L, failedBuild.count());
        assertEquals(30_000.0, failedBuild.totalTime(TimeUnit.MILLISECONDS));
        assertEquals(1L, fetch.count());
        assertEquals(120.0, fetch.totalTime(TimeUnit.MILLISECONDS));
        assertEquals(0L, registry.get(AgentMeters.REDEPLOY_PHASE_DURATION)
                .tags("phase", "build", "outcome", "success").functionTimer().count());
    }

    @Test
    void theAgeOfTheLastSuccessIsReadFromDiskAfterARestart(@TempDir Path dir) throws IOException {
        serviceIn(dir).record(success());
        clock.advance(Duration.ofSeconds(90));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        metered(dir, registry);

        assertEquals(90.0, registry.get(AgentMeters.REDEPLOY_LAST_SUCCESS_AGE).gauge().value());
    }

    @Test
    void theAgeIsNotANumberBeforeAnySuccess(@TempDir Path dir) throws IOException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        metered(dir, registry).record(failure("fetch", null));

        assertTrue(Double.isNaN(registry.get(AgentMeters.REDEPLOY_LAST_SUCCESS_AGE).gauge().value()),
                "zero would claim a redeploy just worked");
    }

    private RedeployHistoryService serviceIn(Path dir) {
        return new RedeployHistoryService(dir.resolve(RedeployHistoryService.HISTORY_FILE),
                AgentMeters.disabled(), clock);
    }

    private RedeployHistoryService metered(Path dir, SimpleMeterRegistry registry) {
        RedeployHistoryService service = new RedeployHistoryService(
                dir.resolve(RedeployHistoryService.HISTORY_FILE), new AgentMeters(registry), clock);
        service.publishMeters();
        return service;
    }

    private static long total(RedeployHistory history, String outcome, String failedPhase) {
        return history.totals().stream()
                .filter(total -> total.outcome().equals(outcome) && total.failedPhase().equals(failedPhase))
                .mapToLong(total -> total.count())
                .sum();
    }

    private static RedeployReport success() {
        Map<String, Long> phases = new LinkedHashMap<>();
        phases.put("verify", 4_000L);
        phases.put("fetch", 120L);
        phases.put("promote", 5L);
        phases.put("build", 60_000L);
        phases.put("restart", 9_000L);
        return new RedeployReport(T0.minusSeconds(90), "success", null, phases, null);
    }

    private static RedeployReport failure(String failedPhase, String message) {
        Map<String, Long> phases = new LinkedHashMap<>();
        phases.put("fetch", 120L);
        phases.put("promote", 5L);
        phases.put(failedPhase, 30_000L);
        return new RedeployReport(T0.minusSeconds(60), "failure", failedPhase, phases, message);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
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
