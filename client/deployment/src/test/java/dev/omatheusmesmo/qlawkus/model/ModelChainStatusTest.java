package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.faulttolerance.api.CircuitBreakerMaintenance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The console reads the model chain through one snapshot: which tiers exist and what their breakers
 * say, whether a permanent failure is holding the primary out, and which tier answered last. Each
 * of those is a fact the guards already hold, so these tests pin that the snapshot reports them
 * and, above all, that it never calls a model to find out.
 */
@QuarkusTest
class ModelChainStatusTest {

    private static final int THRESHOLD = 3;

    @Inject
    ModelChainStatus status;

    @Inject
    PrimaryChatGuard chatGuard;

    @Inject
    EmbeddingGuard embeddingGuard;

    @BeforeEach
    void clean() {
        CircuitBreakerMaintenance.get().resetAll();
        for (String workload : List.of(WorkloadContext.INTERACTIVE, WorkloadContext.BATCH)) {
            WorkloadContext.runAs(workload, () -> {
                chatGuard.call(() -> response("ok"), () -> response("fallback"));
                embeddingGuard.call(() -> "vector", () -> "fallback-vector");
            });
        }
        ServedTier.forgetLastServed();
    }

    @Test
    void aFreshChainIsClosedEverywhereAndHasServedNothing() {
        ModelChainStatus.Snapshot snapshot = status.snapshot();

        assertEquals(List.of("primary", "secondary", "fallback"),
                snapshot.tiers().stream().map(ModelChainStatus.Tier::name).toList());
        assertEquals("CLOSED", snapshot.tier("primary").state());
        assertTrue(snapshot.permanent().isEmpty());
        assertTrue(snapshot.lastServed().isEmpty());
    }

    @Test
    void anOpenPrimaryBreakerIsShownAsOpen() {
        chatGuard.call(() -> {
            throw new RuntimeException("primary is down");
        }, () -> response("fallback"));

        assertEquals("OPEN", status.snapshot().tier("primary").state());
    }

    @Test
    void aRunOfPermanentFailuresIsNamedByItsStatusWhileTheBreakerStaysClosed() {
        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(RuntimeException.class, () -> chatGuard.call(() -> {
                throw new AuthenticationException(new HttpException(401, "revoked key"));
            }, () -> response("fallback")));
        }

        ModelChainStatus.Snapshot snapshot = status.snapshot();

        assertEquals("CLOSED", snapshot.tier("primary").state());
        assertEquals(1, snapshot.permanent().size());
        assertEquals("chat", snapshot.permanent().getFirst().surface());
        assertEquals(401, snapshot.permanent().getFirst().status());
    }

    @Test
    void fewerPermanentFailuresThanTheThresholdAreNotAVerdict() {
        for (int i = 0; i < THRESHOLD - 1; i++) {
            assertThrows(RuntimeException.class, () -> embeddingGuard.call(() -> {
                throw new AuthenticationException(new HttpException(401, "revoked key"));
            }, () -> "fallback-vector"));
        }

        assertTrue(status.snapshot().permanent().isEmpty());
    }

    @Test
    void theLastInteractiveAnswerNamesItsTier() {
        ServedTier.answered(ServedTier.SECONDARY, response("answer"));

        ModelChainStatus.Served served = status.snapshot().lastServed().orElseThrow();

        assertEquals("secondary", served.tier());
        assertFalse(served.at().isAfter(Instant.now()));
    }

    @Test
    void aBackgroundAnswerIsNotTheTierThatServedTheOwner() {
        ServedTier.answered(ServedTier.PRIMARY, response("owner"));

        WorkloadContext.runAs(WorkloadContext.BATCH, () -> ServedTier.answered(ServedTier.FALLBACK, response("job")));

        assertEquals("primary", status.snapshot().lastServed().orElseThrow().tier());
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }
}
