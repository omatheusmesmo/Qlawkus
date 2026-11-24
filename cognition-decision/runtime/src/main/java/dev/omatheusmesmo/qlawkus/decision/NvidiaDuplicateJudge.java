package dev.omatheusmesmo.qlawkus.decision;

import java.time.Duration;

import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.omatheusmesmo.qlawkus.cognition.review.DuplicateJudge;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Tells the nightly memory review whether two close facts are one fact said twice. The question is
 * yes only for a plain rewording: a correction, a negation, a change of date, name or number answers
 * no, and so does anything the model is unsure of, since the threshold is high and a fact deleted in
 * error is gone. Disabled or without a key it answers yes, which is the review's behavior without a
 * judge. It has its own guard, so its breaker never shares state with the turn gate or the chat models.
 */
@ApplicationScoped
public class NvidiaDuplicateJudge implements DuplicateJudge {

    static final String QUESTION_ID = "duplicate-fact";
    private static final String QUESTION = "Do Statement A and Statement B state exactly the same fact, only worded "
            + "differently? Answer No if one negates, corrects, updates or narrows the other, or if they differ in "
            + "any detail such as a date, a name, a number or a preference that has changed.";

    private final NvidiaDecisionConfig config;
    private final NvidiaDecisionModel model;
    private final AgentMeters meters;
    private final Tracer tracer;

    @Inject
    NvidiaDuplicateJudge(NvidiaDecisionConfig config, @Identifier("nvidia-turn-gate") NvidiaDecisionModel model,
            AgentMeters meters, Instance<Tracer> tracer) {
        this(config, model, meters, tracer.isResolvable() ? tracer.get() : OpenTelemetry.noop().getTracer("qlawkus.decision"));
    }

    NvidiaDuplicateJudge(NvidiaDecisionConfig config, NvidiaDecisionModel model, AgentMeters meters, Tracer tracer) {
        this.config = config;
        this.model = model;
        this.meters = meters;
        this.tracer = tracer;
    }

    @Override
    @Timeout(5000)
    @Bulkhead(2)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 60000)
    public boolean sameFact(String kept, String candidate) {
        if (!config.enabled() || !model.configured()) {
            return true;
        }
        var request = DecisionRequest.builder().input("Statement A: " + kept + "\nStatement B: " + candidate)
                .question(QUESTION_ID, YesNoQuestion.from(QUESTION)).build();
        long started = System.nanoTime();
        var span = tracer.spanBuilder("qlawkus.decision.nvidia")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.provider.name", "nvidia")
                .setAttribute("gen_ai.request.model", config.model())
                .setAttribute("qlawkus.decision.question", QUESTION_ID)
                .startSpan();
        try (var scope = span.makeCurrent()) {
            return model.decide(request).yesNo(QUESTION_ID).probability() >= config.duplicateThreshold();
        } catch (RuntimeException e) {
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
            meters.turnGateDuration(QUESTION_ID, Duration.ofNanos(System.nanoTime() - started));
        }
    }
}
