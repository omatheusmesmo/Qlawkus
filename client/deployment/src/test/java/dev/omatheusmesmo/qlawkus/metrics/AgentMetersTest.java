package dev.omatheusmesmo.qlawkus.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the embedding meters to the operation that can actually fail.
 *
 * <p>The reason these exist: the first cut recorded the fan-out from inside {@code FactChunker},
 * which runs <em>before</em> anything is embedded and therefore had no outcome to report. It passed
 * a hardcoded success, so {@code qlawkus.embedding.facts{outcome="failure"}} was a series that could
 * never appear - and a failed embed is precisely the condition the metric was justified by, since it
 * leaves an orphaned markdown file that aborts the reconcile on the next boot.
 */
class AgentMetersTest {

    private static final String OUTCOME = "outcome";

    /**
     * The regression. An embed that throws must be counted as a failure, because a fact that never
     * embedded is the failure mode the whole embedding metric exists to surface.
     */
    @Test
    void aFailedEmbeddingIsCountedAsAFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMeters meters = new AgentMeters(registry);

        assertThrows(IllegalStateException.class, () -> meters.embedFact(1, () -> {
            throw new IllegalStateException("embedding provider is down");
        }));

        assertEquals(1.0, registry.get(AgentMeters.EMBEDDING_FACTS).tag(OUTCOME, "failure")
                .counter().count(), "a fact that failed to embed must be counted as such");
    }

    /**
     * Telemetry observes, it never swallows. The store's own error handling has to see the original
     * exception with its type and message intact.
     */
    @Test
    void aFailedEmbeddingPropagatesTheOriginalException() {
        AgentMeters meters = new AgentMeters(new SimpleMeterRegistry());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> meters.embedFact(1, () -> {
                    throw new IllegalStateException("token limit exceeded");
                }));

        assertEquals("token limit exceeded", thrown.getMessage(),
                "the exception must reach the caller unchanged");
    }

    @Test
    void aSuccessfulEmbeddingIsCountedAndReportsItsFanOut() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMeters meters = new AgentMeters(registry);

        meters.embedFact(3, () -> {
        });

        assertEquals(1.0, registry.get(AgentMeters.EMBEDDING_FACTS).tag(OUTCOME, "success")
                .counter().count());
        assertEquals(3.0, registry.get(AgentMeters.EMBEDDING_SEGMENTS).summary().totalAmount(),
                "one fact becoming three segments is the fan-out worth seeing");
    }

    /**
     * A fact split into several segments is one that did not fit the embedding budget whole. A
     * single-segment fact must not be counted, or the series stops meaning anything.
     */
    @Test
    void onlyASplitFactCountsAsOversized() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMeters meters = new AgentMeters(registry);

        meters.embedFact(1, () -> {
        });

        assertNull(registry.find(AgentMeters.EMBEDDING_OVERSIZED).counter(),
                "a fact that fit in one segment was never oversized");

        meters.embedFact(2, () -> {
        });

        assertEquals(1.0, registry.get(AgentMeters.EMBEDDING_OVERSIZED).counter().count());
    }

    /**
     * Without the observability extension there is no registry, and every meter call must be inert
     * rather than a boot failure. The stores construct meters this way outside CDI.
     */
    @Test
    void meteringIsInertWithoutARegistry() {
        AgentMeters meters = AgentMeters.disabled();

        meters.embedFact(2, () -> {
        });

        assertEquals(false, meters.enabled(), "no registry means telemetry stays off");
    }
}
