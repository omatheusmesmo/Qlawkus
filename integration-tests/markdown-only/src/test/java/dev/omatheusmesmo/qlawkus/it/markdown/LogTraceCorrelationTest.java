package dev.omatheusmesmo.qlawkus.it.markdown;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that a log line written inside a span carries that span's ids, which is what makes a log and
 * a trace one investigation instead of two: find the slow turn in the trace, then filter the logs on
 * its {@code traceId}. quarkus-opentelemetry writes the ids into the MDC and the JSON formatter emits
 * the MDC, so selecting the {@code observability} capability is all it takes.
 *
 * <p>It runs on the shipped configuration on purpose. {@code quarkus.otel.sdk.disabled=true} stops
 * the export, not the spans, so the ids reach the logs before any collector exists.
 */
@QuarkusTest
class LogTraceCorrelationTest {

    @Inject
    Tracer tracer;

    @Test
    void aLogLineInsideASpanCarriesItsTraceAndSpanIds() {
        Span span = tracer.spanBuilder("turn").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            assertTrue(span.getSpanContext().isValid(),
                    "a no-op span has all-zero ids, which would make the comparison below vacuous");
            assertEquals(span.getSpanContext().getTraceId(), MDC.get("traceId"),
                    "without the trace id in the MDC a log line cannot be found from its trace");
            assertEquals(span.getSpanContext().getSpanId(), MDC.get("spanId"));
        } finally {
            span.end();
        }
    }
}
