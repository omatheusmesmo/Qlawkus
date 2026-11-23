package dev.omatheusmesmo.qlawkus.agent;

import static dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Status.BUSY;
import static dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Status.CANCELLED;
import static dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Status.COMPLETE;
import static dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Status.FAILED;
import static dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Status.SNAPSHOT_LIMIT;
import static dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Status.TIMEOUT;
import static dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Status.TOKEN_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Pattern;
import dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Plan;
import dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Role;
import dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Source;
import dev.omatheusmesmo.qlawkus.agent.SpecialistEvaluation.Worker;
import dev.omatheusmesmo.qlawkus.artifact.ArtifactHandle;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BoundedSpecialistDelegationTest {

    @Inject
    SpecialistEvaluation.Team workflow;

    @Inject
    AgentDeliveryContext delivery;

    @Test
    void runsTwoIsolatedSpecialistsInParallelAndReturnsEvidenceToTheParent() throws Exception {
        CountDownLatch bothModels = new CountDownLatch(2);
        Instant now = Instant.now();
        var approved = new ArtifactHandle(UUID.randomUUID(), "approved.md", "text/markdown", 20, now, now.plusSeconds(60));

        delivery.set("owner-conversation", "discord", "owner-chat");
        try (var calls = Executors.newVirtualThreadPerTaskExecutor()) {
            var prototype = new SpecialistEvaluation(workflow, calls);
            var report = prototype.evaluate(prototype.newRun(
                    new Source("Facts snapshot", checkingModel("Facts snapshot", "The build succeeded.", bothModels), approved),
                    new Source("Questions snapshot", checkingModel("Questions snapshot", "Who reviews the release?", bothModels), null),
                    Duration.ofSeconds(5)));
            assertEquals(List.of(COMPLETE, COMPLETE), report.results().stream().map(SpecialistEvaluation.Result::status).toList());
            assertEquals(List.of(approved), report.artifacts());
            assertEquals(46, report.results().stream().mapToInt(SpecialistEvaluation.Result::reportedTokens).sum());
            assertTrue(report.results().stream().allMatch(result -> result.activities().contains("read snapshot")));
            assertTrue(report.answer().contains("The build succeeded."));
            assertTrue(report.answer().contains("Who reviews the release?"));

            var next = prototype.evaluate(prototype.newRun(
                    new Source("Next parent run", model(request -> {
                        assertEquals("Next parent run", snapshot(request));
                        assertEquals(2, request.messages().size());
                        return answer("New result", 20, 3);
                    }), null),
                    new Source("Other snapshot", model(request -> answer("Other result", 20, 3)), null),
                    Duration.ofSeconds(5)));
            assertFalse(next.answer().contains("release"));
            assertTrue(next.artifacts().isEmpty());
            assertTrue(delivery.hasDeliveryInfo());
            assertEquals(WorkloadContext.INTERACTIVE, WorkloadContext.current());
        } finally {
            delivery.clear();
        }
    }

    @Test
    void boundsInputsAndOutputsAndKeepsTheOtherSpecialistsResult() {
        try (var calls = Executors.newVirtualThreadPerTaskExecutor()) {
            var prototype = new SpecialistEvaluation(workflow, calls);
            var report = prototype.evaluate(prototype.newRun(
                    new Source("data", model(request -> answer("Over budget", 1000, 100)), null),
                    new Source("second", model(request -> answer("Usable evidence", 20, 3)), null),
                    Duration.ofSeconds(5)));
            assertEquals(TOKEN_LIMIT, report.results().getFirst().status());
            assertEquals(COMPLETE, report.results().getLast().status());
            assertFalse(report.answer().contains("Over budget"));

            var failed = prototype.evaluate(prototype.newRun(
                    new Source("ç".repeat(SpecialistEvaluation.MAX_SNAPSHOT_BYTES), model(request -> {
                        throw new AssertionError("Oversized input must be rejected before a model call");
                    }), null),
                    new Source("second", model(request -> {
                        throw new IllegalStateException("private provider detail");
                    }), null),
                    Duration.ofSeconds(5)));
            assertEquals(SNAPSHOT_LIMIT, failed.results().getFirst().status());
            assertEquals(FAILED, failed.results().getLast().status());
            assertFalse(failed.answer().contains("private provider detail"));
        }
    }

    @Test
    void createsNamedWorkersAndSelectsSequenceOrParallelInRuntime() {
        var facts = new Worker("release-facts", Role.FACTS, "Check the release facts.");
        var questions = new Worker("release-questions", Role.QUESTIONS, "Collect missing review information.");
        var workers = List.of(questions, facts);
        assertThrows(IllegalArgumentException.class,
                () -> new Plan(Pattern.PARALLEL, List.of(facts, facts)));
        try (var calls = Executors.newVirtualThreadPerTaskExecutor();
                var orchestration = Executors.newVirtualThreadPerTaskExecutor()) {
            var prototype = new SpecialistEvaluation(workflow, calls);
            var observed = new ArrayList<String>();
            var sequenced = prototype.evaluateRuntime(prototype.newRun(
                    new Source("facts", model(request -> {
                        assertTrue(((SystemMessage) request.messages().getFirst()).text().contains(facts.instructions()));
                        observed.add(snapshot(request));
                        return answer("Release facts", 20, 3);
                    }), null),
                    new Source("questions", model(request -> {
                        observed.add(snapshot(request));
                        return answer("Review questions", 20, 3);
                    }), null), Duration.ofSeconds(5)),
                    new Plan(Pattern.SEQUENCE, workers), orchestration);
            assertEquals(List.of("questions", "facts"), observed);
            assertEquals(List.of("release-questions", "release-facts"),
                    sequenced.results().stream().map(SpecialistEvaluation.Result::specialist).toList());
            assertTrue(sequenced.answer().contains("Release facts"));

            var both = new CountDownLatch(2);
            var parallel = prototype.evaluateRuntime(prototype.newRun(
                    new Source("facts", checkingModel("facts", "Release facts", both), null),
                    new Source("questions", checkingModel("questions", "Review questions", both), null),
                    Duration.ofSeconds(5)), new Plan(Pattern.PARALLEL, workers), orchestration);
            assertEquals(List.of(COMPLETE, COMPLETE),
                    parallel.results().stream().map(SpecialistEvaluation.Result::status).toList());
            assertTrue(parallel.answer().contains("Review questions"));
        }
    }

    @Test
    void rejectsAWorkerThatTriesToCallAnUnapprovedTool() {
        var call = ToolExecutionRequest.builder().id("call-not-allowed").name("sendArtifact").arguments("{}").build();
        try (var calls = Executors.newVirtualThreadPerTaskExecutor()) {
            var prototype = new SpecialistEvaluation(workflow, calls);
            var report = prototype.evaluate(prototype.newRun(
                    new Source("data", model(request -> ChatResponse.builder().aiMessage(AiMessage.from(call))
                            .tokenUsage(new TokenUsage(20, 3)).build()), null),
                    new Source("second", model(request -> answer("Usable evidence", 20, 3)), null),
                    Duration.ofSeconds(5)));
            assertEquals(FAILED, report.results().getFirst().status());
            assertEquals(COMPLETE, report.results().getLast().status());
            assertFalse(report.answer().contains("sendArtifact"));
        }
    }

    @Test
    void sharesTheDeadlineAndInterruptsATimedOutWorker() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        try (var calls = Executors.newVirtualThreadPerTaskExecutor()) {
            var prototype = new SpecialistEvaluation(workflow, calls);
            var report = prototype.evaluate(prototype.newRun(
                    new Source("slow", blockingModel(new CountDownLatch(1), interrupted), null),
                    new Source("quick", model(request -> answer("Ready", 20, 3)), null),
                    Duration.ofSeconds(2)));
            assertEquals(TIMEOUT, report.results().getFirst().status());
            assertEquals(COMPLETE, report.results().getLast().status());
            assertTrue(interrupted.await(2, TimeUnit.SECONDS), "the model call was interrupted");
        }
    }

    @Test
    void cancelsTheGroupAndRejectsOverlappingGroups() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch interrupted = new CountDownLatch(2);
        try (var calls = Executors.newVirtualThreadPerTaskExecutor();
                var parents = Executors.newVirtualThreadPerTaskExecutor()) {
            var prototype = new SpecialistEvaluation(workflow, calls);
            var source = new Source("waiting", blockingModel(entered, interrupted), null);
            var run = prototype.newRun(source, source, Duration.ofSeconds(10));
            var pending = parents.submit(() -> prototype.evaluate(run));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS), "both model calls started");
                var second = prototype.evaluate(prototype.newRun(source, source, Duration.ofSeconds(10)));
                assertTrue(second.results().stream().allMatch(result -> result.status() == BUSY));
            } finally {
                run.cancel();
            }
            assertTrue(pending.get(5, TimeUnit.SECONDS).results().stream().allMatch(result -> result.status() == CANCELLED));
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        }
    }

    private ChatModel checkingModel(String expected, String evidence, CountDownLatch bothModels) {
        return model(request -> {
            assertEquals(expected, snapshot(request));
            assertEquals(2, request.messages().size());
            assertEquals(SpecialistEvaluation.MAX_OUTPUT_TOKENS, request.parameters().maxOutputTokens());
            assertTrue(request.parameters().toolSpecifications().isEmpty());
            assertFalse(delivery.hasDeliveryInfo());
            assertEquals(WorkloadContext.BATCH, WorkloadContext.current());
            bothModels.countDown();
            try {
                assertTrue(bothModels.await(3, TimeUnit.SECONDS), "both model calls run concurrently");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return answer(evidence, 20, 3);
        });
    }

    private static ChatModel blockingModel(CountDownLatch entered, CountDownLatch interrupted) {
        return model(request -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("latch cannot open");
            } catch (InterruptedException failure) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        });
    }

    private static String snapshot(ChatRequest request) {
        return ((UserMessage) request.messages().getLast()).singleText();
    }

    private static ChatModel model(Function<ChatRequest, ChatResponse> function) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return function.apply(request);
            }
        };
    }

    private static ChatResponse answer(String text, int input, int output) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).tokenUsage(new TokenUsage(input, output)).build();
    }
}
