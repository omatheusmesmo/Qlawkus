package dev.omatheusmesmo.qlawkus.heartbeat;

import dev.omatheusmesmo.qlawkus.heartbeat.HeartbeatRun.Outcome;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import dev.omatheusmesmo.qlawkus.task.OwnerChatDirectory;
import dev.omatheusmesmo.qlawkus.task.OwnerNoticeDelivery;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

@ApplicationScoped
public class HeartbeatService {

    private static final String TASK_NOTICE = "schedules";
    private static final String MEMORY_NOTICE = "memory";

    private final HeartbeatConfig config;
    private final HeartbeatStateFile store;
    private final OwnerChatDirectory chats;
    private final Supplier<OwnerNoticeDelivery> noticeDelivery;
    private final Supplier<HeartbeatObservations> observations;
    private final HeartbeatDecisionModel decision;
    private final Clock clock;
    private final Executor executor;
    private final AgentMeters meters;
    private final Consumer<HeartbeatSettings> settingsValidator;
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile HeartbeatState state;
    private volatile boolean storageAvailable = true;

    @Inject
    HeartbeatService(HeartbeatConfig config, HeartbeatStateFile store, OwnerChatDirectory chats,
            Instance<OwnerNoticeDelivery> noticeDelivery, HeartbeatObservationSource observations,
            GuardedHeartbeatDecision decision, @VirtualThreads ExecutorService executor, AgentMeters meters,
            HeartbeatSettingsValidator settingsValidator) {
        this(config, store, chats, () -> noticeDelivery.isResolvable() ? noticeDelivery.get() : null,
                observations::read, decision::decide, Clock.systemUTC(), executor, meters, settingsValidator::validate);
    }

    HeartbeatService(HeartbeatConfig config, HeartbeatStateFile store, OwnerChatDirectory chats,
            Supplier<OwnerNoticeDelivery> noticeDelivery, Supplier<HeartbeatObservations> observations,
            HeartbeatDecisionModel decision, Clock clock, Executor executor, AgentMeters meters,
            Consumer<HeartbeatSettings> settingsValidator) {
        this.config = config;
        this.store = store;
        this.chats = chats;
        this.noticeDelivery = noticeDelivery;
        this.observations = observations;
        this.decision = decision;
        this.clock = clock;
        this.executor = executor;
        this.meters = meters;
        this.settingsValidator = settingsValidator;
        state = initialState();
    }

    public HeartbeatStatus status() {
        HeartbeatState current = state;
        return new HeartbeatStatus(current.settings(), current.nextRun(), current.lastRun(), config.minimumIntervalMinutes(),
                config.maximumDailyRuns(), Math.max(0, config.maximumDailyRuns() - usedToday(current, clock.instant())),
                config.maximumDecisionTokens(), config.deadlineSeconds(),
                chats.knownChats().stream().map(OwnerChat::providerId).filter(this::providerAvailable)
                        .distinct().sorted().toList(),
                currentTarget(current).isPresent(), storageAvailable);
    }

    public synchronized HeartbeatStatus configure(HeartbeatSettings settings) {
        Objects.requireNonNull(settings, "Heartbeat settings are required.");
        validateSettings(settings);
        HeartbeatState previous = state;
        String boundChat = bindTarget(settings, previous);
        if (storageAvailable && previous.settings().equals(settings) && Objects.equals(previous.boundChat(), boundChat)) {
            return status();
        }
        Instant now = clock.instant();
        boolean sameTarget = Objects.equals(previous.settings().provider(), settings.provider())
                && Objects.equals(previous.boundChat(), boundChat);
        Instant next = settings.enabled() ? nextScheduled(now, settings) : null;
        if (next != null && previous.nextRun() != null && next.isBefore(previous.nextRun()) && previous.failures() > 0) {
            next = previous.nextRun();
        }
        save(new HeartbeatState(settings, boundChat, previous.revision() + 1, next, previous.day(), previous.usedRuns(),
                previous.failures(), interrupt(previous.lastRun(), now), sameTarget ? previous.notices() : Map.of()));
        return status();
    }

    private String bindTarget(HeartbeatSettings settings, HeartbeatState previous) {
        if (settings.provider() == null) {
            return null;
        }
        var target = knownTarget(settings.provider());
        if (target.isEmpty() && !settings.enabled() && settings.provider().equals(previous.settings().provider())) {
            return previous.boundChat();
        }
        return target.orElseThrow(() -> new IllegalArgumentException("Choose a provider with an accepted owner conversation."))
                .chatId();
    }

    void tick() {
        HeartbeatState current = state;
        Instant now = clock.instant();
        if (!due(current, now)) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            observe(Outcome.OVERLAP_SKIPPED, Duration.ZERO);
            return;
        }
        HeartbeatState claim;
        try {
            claim = reserve();
        } catch (RuntimeException failure) {
            running.set(false);
            observe(Outcome.STORAGE_UNAVAILABLE, Duration.ZERO);
            Log.warn("Could not update persisted heartbeat state.");
            return;
        }
        if (claim == null) {
            running.set(false);
            return;
        }
        try {
            run(claim);
        } catch (RuntimeException failure) {
            observe(Outcome.STORAGE_UNAVAILABLE, Duration.ZERO);
            Log.warn("Could not update persisted heartbeat state.");
        }
    }

    private void run(HeartbeatState claim) {
        Instant deadline = claim.lastRun().startedAt().plusSeconds(config.deadlineSeconds());
        FutureTask<RunResult> job = new FutureTask<>(() -> execute(claim, deadline));
        try {
            executor.execute(() -> {
                try {
                    job.run();
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException failure) {
            running.set(false);
            complete(claim, failed(Outcome.OBSERVATION_FAILED));
            return;
        }
        RunResult result;
        try {
            result = job.get(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException failure) {
            result = failed(Outcome.TIMEOUT);
            job.cancel(true);
        } catch (InterruptedException failure) {
            result = failed(Outcome.INTERRUPTED);
            job.cancel(true);
            Thread.currentThread().interrupt();
        } catch (ExecutionException failure) {
            result = failed(Outcome.OBSERVATION_FAILED);
        }
        complete(claim, result);
    }

    private RunResult execute(HeartbeatState claim, Instant deadline) {
        HeartbeatObservations snapshot;
        try {
            snapshot = observations.get();
        } catch (RuntimeException failure) {
            return failed(Outcome.OBSERVATION_FAILED);
        }
        if (!sameRun(claim) || remaining(deadline).isZero() || Thread.currentThread().isInterrupted()) {
            return new RunResult(Outcome.TIMEOUT, snapshot, 0);
        }
        HeartbeatDecision reply;
        boolean targetAvailable = currentTarget(claim).isPresent();
        try {
            reply = decision.decide(snapshot.decisionFields(claim.settings().prompt(), targetAvailable));
        } catch (org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException failure) {
            return new RunResult(Outcome.TIMEOUT, snapshot, 0);
        } catch (RuntimeException failure) {
            return new RunResult(Outcome.DECISION_FAILED, snapshot, 0);
        }
        if (reply == null || reply.tokens() < 0 || reply.tokens() > config.maximumDecisionTokens()) {
            return new RunResult(Outcome.BUDGET_EXHAUSTED, snapshot, reply == null ? 0 : Math.max(0, reply.tokens()));
        }
        if (remaining(deadline).isZero() || Thread.currentThread().isInterrupted()) {
            return new RunResult(Outcome.TIMEOUT, snapshot, reply.tokens());
        }
        Outcome outcome = switch (reply.action()) {
            case NO_OP -> Outcome.NO_OP;
            case CHECK_SCHEDULES, CHECK_MEMORY -> Outcome.OBSERVED;
            case NOTIFY_SCHEDULES -> snapshot.taskIssues() == 0 ? Outcome.NO_SIGNAL
                    : notifyOwner(claim, deadline, TASK_NOTICE, snapshot.taskFingerprint(),
                            "Heartbeat found " + snapshot.taskIssues()
                                    + " missed or invalid scheduled tasks. Open Schedule to review them.");
            case NOTIFY_MEMORY -> snapshot.hasCuratedMemory() ? Outcome.NO_SIGNAL
                    : notifyOwner(claim, deadline, MEMORY_NOTICE, "empty", "Heartbeat found no curated memory.");
        };
        return new RunResult(outcome, snapshot, reply.tokens());
    }

    private synchronized HeartbeatState reserve() {
        HeartbeatState current = state;
        Instant now = clock.instant();
        if (!due(current, now) || (current.lastRun() != null && current.lastRun().outcome() == Outcome.RUNNING)) {
            return null;
        }
        int used = usedToday(current, now);
        LocalDate day = today(now).isAfter(current.day()) ? today(now) : current.day();
        if (used >= config.maximumDailyRuns()) {
            Instant tomorrow = day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            HeartbeatRun skipped = new HeartbeatRun(now, now, Outcome.BUDGET_EXHAUSTED, 0, null, null);
            save(new HeartbeatState(current.settings(), current.boundChat(), current.revision(),
                    latest(tomorrow, nextScheduled(now, current.settings())), day, used, current.failures(),
                    skipped, current.notices()));
            observe(Outcome.BUDGET_EXHAUSTED, Duration.ZERO);
            return null;
        }
        HeartbeatState claimed = new HeartbeatState(current.settings(), current.boundChat(), current.revision(),
                nextScheduled(now, current.settings()), day, used + 1, current.failures(),
                new HeartbeatRun(now, null, Outcome.RUNNING, 0, null, null), current.notices());
        save(claimed);
        return claimed;
    }

    private Outcome notifyOwner(HeartbeatState claim, Instant deadline, String kind, String fingerprint, String message) {
        TaskDestination destination;
        OwnerNoticeDelivery delivery;
        synchronized (this) {
            if (!sameRun(claim) || remaining(deadline).isZero() || Thread.currentThread().isInterrupted()) {
                return Outcome.INTERRUPTED;
            }
            if (fingerprint.equals(state.notices().get(kind))) {
                return Outcome.DUPLICATE;
            }
            destination = currentTarget(claim).orElse(null);
            delivery = noticeDelivery.get();
            if (destination == null || delivery == null) {
                return Outcome.DESTINATION_UNAVAILABLE;
            }

            var notices = new HashMap<>(state.notices());
            notices.put(kind, fingerprint);
            HeartbeatState current = state;
            save(new HeartbeatState(current.settings(), current.boundChat(), current.revision(), current.nextRun(),
                    current.day(), current.usedRuns(), current.failures(), current.lastRun(), notices));
        }
        Duration budget = remaining(deadline);
        if (budget.isZero() || Thread.currentThread().isInterrupted()) {
            return Outcome.TIMEOUT;
        }
        try {
            delivery.send(destination, message, budget);
            return Outcome.NOTIFIED;
        } catch (RuntimeException failure) {
            return Outcome.DELIVERY_FAILED;
        }
    }

    private synchronized void complete(HeartbeatState claim, RunResult result) {
        if (!sameRun(claim)) {
            return;
        }
        HeartbeatState current = state;
        Instant now = clock.instant();
        var snapshot = result.observations();
        HeartbeatRun finished = new HeartbeatRun(claim.lastRun().startedAt(), now, result.outcome(), result.tokens(),
                snapshot == null ? null : snapshot.taskIssues(), snapshot == null ? null : snapshot.hasCuratedMemory());
        int failures = failedRun(result.outcome()) ? Math.min(5, current.failures() + 1) : 0;
        Instant next = latest(current.nextRun(), nextScheduled(now, current.settings()));
        if (failures > 0) {
            next = latest(next, now.plus(Duration.ofMinutes(1L << failures)));
        }
        var notices = new HashMap<>(current.notices());
        if (snapshot != null) {
            if (snapshot.hasCuratedMemory()) {
                notices.remove(MEMORY_NOTICE);
            }
            if (snapshot.taskIssues() == 0) {
                notices.remove(TASK_NOTICE);
            }
        }
        save(new HeartbeatState(current.settings(), current.boundChat(), current.revision(), next,
                current.day(), current.usedRuns(), failures, finished, notices));
        observe(result.outcome(), Duration.between(finished.startedAt(), now).abs());
        Log.infof("Heartbeat run finished outcome=%s tokens=%d", result.outcome(), result.tokens());
    }

    private HeartbeatState initialState() {
        Instant now = clock.instant();
        try {
            HeartbeatState loaded = store.load();
            if (loaded != null) {
                HeartbeatSettings settings = loaded.settings();
                validateSettings(settings);
                var next = settings.enabled() ? latest(loaded.nextRun(), nextScheduled(now, settings)) : null;
                var recovered = new HeartbeatState(settings, loaded.boundChat(), loaded.revision(), next, loaded.day(),
                        loaded.usedRuns(), loaded.failures(), interrupt(loaded.lastRun(), now), loaded.notices());
                if (loaded.lastRun() != null && loaded.lastRun().outcome() == Outcome.RUNNING) {
                    store.save(recovered);
                }
                return recovered;
            }
            var settings = new HeartbeatSettings(config.enabled(), Math.max(config.intervalMinutes(), config.minimumIntervalMinutes()),
                    config.prompt().orElse(null), config.provider().orElse(null));
            validateSettings(settings);
            String bound = settings.provider() == null ? null : knownTarget(settings.provider()).map(TaskDestination::chatId)
                    .orElse(null);
            return new HeartbeatState(settings, bound, 0, settings.enabled() ? nextScheduled(now, settings) : null,
                    today(now), 0, 0, null, Map.of());
        } catch (IOException | RuntimeException failure) {
            storageAvailable = false;
            Log.warn("Could not load heartbeat state. The heartbeat stays paused until new settings are saved.");
            return new HeartbeatState(new HeartbeatSettings(false, Math.max(5, config.minimumIntervalMinutes()), "", null),
                    null, 0, null, today(now), 0, 0, null, Map.of());
        }
    }

    private void save(HeartbeatState value) {
        try {
            store.save(value);
            state = value;
            storageAvailable = true;
        } catch (RuntimeException failure) {
            storageAvailable = false;
            throw failure;
        }
    }

    private boolean due(HeartbeatState current, Instant now) {
        return storageAvailable && current.settings().enabled() && current.nextRun() != null
                && !current.nextRun().isAfter(now);
    }

    private boolean sameRun(HeartbeatState claim) {
        HeartbeatState current = state;
        return current.revision() == claim.revision() && current.lastRun() != null
                && current.lastRun().equals(claim.lastRun()) && storageAvailable;
    }

    private Optional<TaskDestination> currentTarget(HeartbeatState claim) {
        return claim.settings().provider() == null ? Optional.empty()
                : knownTarget(claim.settings().provider()).filter(target -> target.chatId().equals(claim.boundChat()));
    }

    private Optional<TaskDestination> knownTarget(String provider) {
        return providerAvailable(provider) ? chats.lastChat(provider).filter(chat -> chat.providerId().equals(provider))
                .map(chat -> new TaskDestination(provider, chat.chatId())) : Optional.empty();
    }

    private boolean providerAvailable(String provider) {
        OwnerNoticeDelivery delivery = noticeDelivery.get();
        return delivery != null && delivery.isAvailable(provider);
    }

    private void validateSettings(HeartbeatSettings settings) {
        settingsValidator.accept(settings);
        if (settings.intervalMinutes() < config.minimumIntervalMinutes()) {
            throw new IllegalArgumentException("The interval is below the configured heartbeat minimum.");
        }
    }

    private void observe(Outcome outcome, Duration elapsed) {
        meters.heartbeat(outcome.name().toLowerCase(java.util.Locale.ROOT), elapsed, !failedRun(outcome));
    }

    private static boolean failedRun(Outcome outcome) {
        return switch (outcome) {
            case TIMEOUT, DECISION_FAILED, OBSERVATION_FAILED, DELIVERY_FAILED, DESTINATION_UNAVAILABLE,
                    STORAGE_UNAVAILABLE -> true;
            default -> false;
        };
    }

    private Duration remaining(Instant deadline) {
        Duration duration = Duration.between(clock.instant(), deadline);
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    private static LocalDate today(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC);
    }

    private static int usedToday(HeartbeatState state, Instant now) {
        return today(now).isAfter(state.day()) ? 0 : state.usedRuns();
    }

    private static Instant nextScheduled(Instant now, HeartbeatSettings settings) {
        return now.plus(Duration.ofMinutes(settings.intervalMinutes()));
    }

    private static Instant latest(Instant first, Instant second) {
        return first == null || first.isBefore(second) ? second : first;
    }

    private static HeartbeatRun interrupt(HeartbeatRun active, Instant now) {
        return active != null && active.outcome() == Outcome.RUNNING
                ? new HeartbeatRun(active.startedAt(), now, Outcome.INTERRUPTED, active.tokens(), null, null) : active;
    }

    private static RunResult failed(Outcome outcome) {
        return new RunResult(outcome, null, 0);
    }

    private record RunResult(Outcome outcome, HeartbeatObservations observations, int tokens) {
    }
}
