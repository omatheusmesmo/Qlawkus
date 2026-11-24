package dev.omatheusmesmo.qlawkus.decision;

import java.time.Duration;
import java.util.List;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.omatheusmesmo.qlawkus.cognition.ConversationFormatter;
import dev.omatheusmesmo.qlawkus.cognition.gate.TurnGate;
import dev.omatheusmesmo.qlawkus.cognition.gate.TurnQuestion;
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

@ApplicationScoped
public class NvidiaTurnGate implements TurnGate {

    private static final int MAX_INPUT_CHARS = 12_000;

    private final NvidiaDecisionConfig config;
    private final DecisionModel model;
    private final AgentMeters meters;
    private final Tracer tracer;

    @Inject
    NvidiaTurnGate(NvidiaDecisionConfig config, @Identifier("nvidia-turn-gate") DecisionModel model,
            AgentMeters meters, Instance<Tracer> tracer) {
        this(config, model, meters, tracer.isResolvable() ? tracer.get() : OpenTelemetry.noop().getTracer("qlawkus.decision"));
    }

    NvidiaTurnGate(NvidiaDecisionConfig config, DecisionModel model, AgentMeters meters, Tracer tracer) {
        this.config = config;
        this.model = model;
        this.meters = meters;
        this.tracer = tracer;
    }

    @Override
    @Timeout(5000)
    @Bulkhead(2)
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 60000)
    public boolean worthIt(TurnQuestion question, List<ChatMessage> messages) {
        if (!config.enabled()) {
            return true;
        }
        var exchange = ConversationFormatter.format(messages.stream()
                .filter(message -> message instanceof UserMessage || message instanceof AiMessage)
                .toList());
        if (exchange.isBlank() || exchange.length() > MAX_INPUT_CHARS) {
            return true;
        }
        var request = DecisionRequest.builder().input(exchange)
                .question(question.id(), YesNoQuestion.from(question.text())).build();
        long started = System.nanoTime();
        var span = tracer.spanBuilder("qlawkus.decision.nvidia")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.provider.name", "nvidia")
                .setAttribute("gen_ai.request.model", config.model())
                .setAttribute("qlawkus.decision.question", question.id())
                .startSpan();
        try (var scope = span.makeCurrent()) {
            return model.decide(request).yesNo(question.id()).probability() >= threshold(question.id());
        } catch (RuntimeException e) {
            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
            meters.turnGateDuration(question.id(), Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private double threshold(String question) {
        return switch (question) {
            case "durable-fact" -> config.durableFactThreshold();
            case "reusable-procedure" -> config.reusableProcedureThreshold();
            case "achievement" -> config.achievementThreshold();
            default -> 0.5;
        };
    }
}
