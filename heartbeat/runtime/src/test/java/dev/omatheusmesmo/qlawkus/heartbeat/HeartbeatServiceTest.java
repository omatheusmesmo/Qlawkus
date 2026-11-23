package dev.omatheusmesmo.qlawkus.heartbeat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.omatheusmesmo.qlawkus.heartbeat.HeartbeatDecision.Action;
import dev.omatheusmesmo.qlawkus.heartbeat.HeartbeatRun.Outcome;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import dev.omatheusmesmo.qlawkus.task.OwnerChatDirectory;
import dev.omatheusmesmo.qlawkus.task.OwnerNoticeDelivery;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatServiceTest {

    @TempDir
    Path directory;

    @Test
    void observesEveryThirtyMinutesByDefaultAndKeepsNoticesLocalWithoutADestination() {
        var fixture = new Fixture();
        var heartbeat = fixture.service();
        assertTrue(heartbeat.status().settings().enabled());
        assertEquals(HeartbeatConfig.DEFAULT_PROMPT, heartbeat.status().settings().prompt());
        fixture.clock.advance(Duration.ofMinutes(29));
        heartbeat.tick();
        assertEquals(0, fixture.modelCalls.get());

        fixture.recommend(Action.NOTIFY_SCHEDULES);
        fixture.minute(heartbeat);
        assertEquals(Outcome.OBSERVED, heartbeat.status().lastRun().outcome());
        assertEquals(2, heartbeat.status().lastRun().taskIssues());
        assertEquals(0, fixture.delivery.sends.get());

        heartbeat.configure(new HeartbeatSettings(true, 1, "Check schedule and memory health.", null));
        fixture.recommend(Action.NO_OP);
        fixture.minute(heartbeat);
        assertEquals(Outcome.NO_OP, heartbeat.status().lastRun().outcome());
        assertEquals(2, fixture.modelCalls.get());
        assertEquals(0, fixture.delivery.sends.get());
        assertEquals(1.0, fixture.meters.counter(AgentMeters.HEARTBEAT_RUNS, "outcome", "no_op").count());
    }

    @Test
    void bindsAndChecksTheKnownDiscordDestinationAndDeduplicatesAcrossRestart() {
        var fixture = new Fixture();
        var heartbeat = fixture.service();
        fixture.recommend(Action.NOTIFY_SCHEDULES);
        assertThrows(IllegalArgumentException.class,
                () -> heartbeat.configure(new HeartbeatSettings(true, 1, "Alert me to broken schedules.", "unknown")));
        heartbeat.configure(new HeartbeatSettings(true, 1, "Alert me to broken schedules.", "discord"));
        fixture.minute(heartbeat);
        assertEquals(Outcome.NOTIFIED, heartbeat.status().lastRun().outcome());
        assertEquals(new TaskDestination("discord", "fixture-owner-chat"), fixture.delivery.target);
        assertEquals(1, fixture.delivery.sends.get());

        var restored = fixture.service();
        restored.tick();
        assertEquals(1, fixture.modelCalls.get());
        fixture.minute(restored);
        assertEquals(Outcome.DUPLICATE, restored.status().lastRun().outcome());
        assertEquals(1, fixture.delivery.sends.get());

        fixture.chats.remember("discord", "different-fixture-chat");
        fixture.observed.set(new HeartbeatObservations(4, 1, "changed", true));
        fixture.minute(restored);
        assertEquals(Outcome.DESTINATION_UNAVAILABLE, restored.status().lastRun().outcome());
        assertEquals(1, fixture.delivery.sends.get());
    }

    @Test
    void consumesDailyBudgetBeforeAModelCallAndRejectsUnbudgetedResponses() {
        var fixture = new Fixture();
        fixture.dailyRuns = 1;
        var heartbeat = fixture.service();
        heartbeat.configure(new HeartbeatSettings(true, 1, "Check for scheduling issues.", "discord"));
        fixture.answer.set(new HeartbeatDecision(Action.NOTIFY_SCHEDULES, 1601));
        fixture.minute(heartbeat);
        assertEquals(Outcome.BUDGET_EXHAUSTED, heartbeat.status().lastRun().outcome());
        assertEquals(0, fixture.delivery.sends.get());
        assertEquals(0, heartbeat.status().remainingDailyRuns());

        fixture.minute(heartbeat);
        assertEquals(1, fixture.modelCalls.get());
        assertEquals(Instant.parse("2026-11-24T00:00:00Z"), heartbeat.status().nextRun());
        var recovered = fixture.service();
        assertEquals(0, recovered.status().remainingDailyRuns());
        fixture.clock.now = Instant.parse("2026-11-24T00:01:00Z");
        fixture.recommend(Action.NO_OP);
        recovered.tick();
        assertEquals(2, fixture.modelCalls.get());
    }

    @Test
    void failedDeliveryBacksOffAndDoesNotReplayAnUncertainSend() {
        var fixture = new Fixture();
        fixture.recommend(Action.NOTIFY_SCHEDULES);
        fixture.delivery.fail = true;
        var heartbeat = fixture.service();
        heartbeat.configure(new HeartbeatSettings(true, 1, "Notify me about problems.", "discord"));

        fixture.minute(heartbeat);
        assertEquals(Outcome.DELIVERY_FAILED, heartbeat.status().lastRun().outcome());
        assertEquals(fixture.clock.instant().plus(Duration.ofMinutes(2)), heartbeat.status().nextRun());
        fixture.minute(heartbeat);
        assertEquals(1, fixture.modelCalls.get());

        fixture.delivery.fail = false;
        fixture.minute(heartbeat);
        assertEquals(Outcome.DUPLICATE, heartbeat.status().lastRun().outcome());
        assertEquals(1, fixture.delivery.sends.get());
    }

    @Test
    void rejectsOverlapAndChoosesTheNextIntervalAfterAFinishedRun() throws Exception {
        var fixture = new Fixture();
        fixture.executor = Thread::startVirtualThread;
        var entered = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        fixture.beforeAnswer = () -> {
            entered.countDown();
            try {
                assertTrue(resume.await(4, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        };
        var heartbeat = fixture.service();
        heartbeat.configure(new HeartbeatSettings(true, 1, "Inspect schedules.", null));
        fixture.clock.advance(Duration.ofMinutes(1));
        Thread first = Thread.startVirtualThread(heartbeat::tick);
        try {
            assertTrue(entered.await(4, TimeUnit.SECONDS));
            fixture.clock.advance(Duration.ofMinutes(4));
            heartbeat.tick();
            assertEquals(Outcome.RUNNING, heartbeat.status().lastRun().outcome());
            assertEquals(1, fixture.modelCalls.get());
            assertEquals(1.0, fixture.meters.counter(AgentMeters.HEARTBEAT_RUNS, "outcome", "overlap_skipped").count());
        } finally {
            resume.countDown();
            first.join(Duration.ofSeconds(4));
        }
        assertEquals(Outcome.TIMEOUT, heartbeat.status().lastRun().outcome());
        assertEquals(fixture.clock.instant().plus(Duration.ofMinutes(2)), heartbeat.status().nextRun());
    }

    @Test
    void interruptedRunIsRecoveredWithoutCatchup() throws Exception {
        var fixture = new Fixture();
        var heartbeat = fixture.service();
        heartbeat.configure(new HeartbeatSettings(true, 1, "Inspect schedules.", null));
        var current = fixture.state.load();
        fixture.state.save(new HeartbeatState(current.settings(), null, current.revision(), current.nextRun(),
                current.day(), 1, 0, new HeartbeatRun(fixture.clock.instant(), null, Outcome.RUNNING, 0, null, null),
                current.notices()));

        fixture.clock.advance(Duration.ofMinutes(20));
        var restored = fixture.service();
        restored.tick();
        assertEquals(Outcome.INTERRUPTED, restored.status().lastRun().outcome());
        assertEquals(Outcome.INTERRUPTED, fixture.state.load().lastRun().outcome());
        assertEquals(0, fixture.modelCalls.get());
        assertEquals(23, restored.status().remainingDailyRuns());
        fixture.minute(restored);
        assertEquals(1, fixture.modelCalls.get());
    }

    private final class Fixture {
        final MutableClock clock = new MutableClock();
        final TestChats chats = new TestChats();
        final TestDelivery delivery = new TestDelivery();
        final SimpleMeterRegistry meters = new SimpleMeterRegistry();
        final HeartbeatStateFile state = new HeartbeatStateFile(directory.resolve("heartbeat.json"),
                new ObjectMapper().registerModule(new JavaTimeModule()));
        final AtomicInteger modelCalls = new AtomicInteger();
        final AtomicReference<HeartbeatDecision> answer = new AtomicReference<>(new HeartbeatDecision(Action.NO_OP, 10));
        final AtomicReference<HeartbeatObservations> observed = new AtomicReference<>(new HeartbeatObservations(3, 2, "fixture", true));
        int dailyRuns = 24;
        Executor executor = Runnable::run;
        Runnable beforeAnswer = () -> {};

        HeartbeatService service() {
            return new HeartbeatService(new TestConfig(dailyRuns), state, chats, () -> delivery, observed::get, request -> {
                modelCalls.incrementAndGet();
                assertFalse(request.toString().contains("fixture-owner-chat"));
                beforeAnswer.run();
                return answer.get();
            }, clock, executor, new AgentMeters(meters), ignored -> {});
        }

        void minute(HeartbeatService service) {
            clock.advance(Duration.ofMinutes(1));
            service.tick();
        }

        void recommend(Action action) {
            answer.set(new HeartbeatDecision(action, 10));
        }
    }

    private record TestConfig(int maximumDailyRuns) implements HeartbeatConfig {
        public int minimumIntervalMinutes() { return 1; }
        public int maximumDecisionTokens() { return 1600; }
        public int deadlineSeconds() { return 15; }
        public boolean enabled() { return true; }
        public int intervalMinutes() { return 30; }
        public Optional<String> prompt() { return Optional.empty(); }
        public Optional<String> provider() { return Optional.empty(); }
    }

    private static final class TestChats implements OwnerChatDirectory {
        private OwnerChat chat = new OwnerChat("discord", "fixture-owner-chat", Instant.EPOCH);
        public void remember(String provider, String chatId) { chat = new OwnerChat(provider, chatId, Instant.EPOCH); }
        public Optional<OwnerChat> lastChat(String provider) {
            return chat.providerId().equals(provider) ? Optional.of(chat) : Optional.empty();
        }
        public Optional<OwnerChat> mostRecent() { return Optional.of(chat); }
    }

    private static final class TestDelivery implements OwnerNoticeDelivery {
        final AtomicInteger sends = new AtomicInteger();
        TaskDestination target;
        boolean fail;
        public boolean isAvailable(String provider) { return provider.equals("discord"); }
        public void send(TaskDestination destination, String notice, Duration remaining) {
            target = destination;
            sends.incrementAndGet();
            assertTrue(notice.startsWith("Heartbeat found"));
            assertFalse(notice.contains("fixture-owner-chat"));
            if (fail) {
                throw new IllegalStateException("sensitive error fixture");
            }
        }
    }

    private static final class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-23T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        void advance(Duration step) { now = now.plus(step); }
    }
}
