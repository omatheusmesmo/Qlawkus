package dev.omatheusmesmo.qlawkus.model;

import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkus.runtime.Startup;
import io.smallrye.faulttolerance.api.TypedGuard;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The retry + circuit breaker + fallback pipeline shared by {@link FallbackChatModel} and {@link
 * FallbackStreamingChatModel}, both of which talk to the same primary completions endpoint over
 * different transports. A {@code TypedGuard} registers its circuit breaker under {@code name()}
 * exactly once - a second guard built with the same name throws "Circuit breaker already exists" -
 * so the two callers cannot each build their own guard and still share breaker state; they share
 * this single {@code @ApplicationScoped} instance instead.
 *
 * <p>The fallback destination differs per caller (a plain blocking call for {@link
 * FallbackChatModel}, a bridged streaming call for {@link FallbackStreamingChatModel}) and per call
 * (the in-flight request and handler), so it cannot be baked into the guard at construction time.
 * {@link #call} carries it through a {@link ThreadLocal}, safe because {@code TypedGuard} invokes
 * every attempt - including the fallback handler - on the calling thread; nothing here is offloaded
 * to another executor.
 *
 * <p>This is also why {@link dev.omatheusmesmo.qlawkus.agent.AgentService} carries no fault-tolerance
 * annotations of its own: protecting one model call here, rather than the whole tool-calling turn at
 * the AI service level, is what keeps a retry from replaying a tool call that already succeeded (see
 * the javadoc on {@code AgentService}).
 */
@ApplicationScoped
@Startup
public class PrimaryChatGuard {

    private static final List<Class<? extends Throwable>> NON_RETRYABLE =
            List.of(NonRetriableException.class, UnsupportedFeatureException.class);

    /**
     * {@code abortOn} also covers an already-open circuit: retrying into a breaker that just rejected
     * the call wastes the backoff budget on certain rejections, so this aborts straight to fallback.
     */
    private static final List<Class<? extends Throwable>> RETRY_ABORTS_ON = concat(NON_RETRYABLE, CircuitBreakerOpenException.class);

    private static List<Class<? extends Throwable>> concat(
            List<Class<? extends Throwable>> base, Class<? extends Throwable> extra) {
        List<Class<? extends Throwable>> combined = new ArrayList<>(base);
        combined.add(extra);
        return List.copyOf(combined);
    }

    private final TypedGuard<ChatResponse> guard;
    private final ThreadLocal<Supplier<ChatResponse>> currentFallback = new ThreadLocal<>();

    @Inject
    public PrimaryChatGuard(ModelFallbackConfig config) {
        this.guard = TypedGuard.create(ChatResponse.class)
                .withRetry()
                .maxRetries(config.retryMaxAttempts())
                .abortOn(RETRY_ABORTS_ON)
                .delay(config.retryInitialDelay().toMillis(), ChronoUnit.MILLIS)
                .withExponentialBackoff()
                .maxDelay(config.retryMaxDelay().toMillis(), ChronoUnit.MILLIS)
                .done()
                .done()
                .withCircuitBreaker()
                .name(ModelFallbackConfig.CIRCUIT_BREAKER_CHAT)
                .requestVolumeThreshold(config.retryMaxAttempts() + 1)
                .failureRatio(1.0)
                .delay(config.circuitBreakerResetTimeout().toMillis(), ChronoUnit.MILLIS)
                .skipOn(NON_RETRYABLE)
                .done()
                .withFallback()
                .skipOn(NON_RETRYABLE)
                .handler(cause -> currentFallback.get().get())
                .done()
                .build();
    }

    /**
     * Runs {@code primary}, retrying and eventually falling back to {@code fallback} per the policy
     * above. Exceptions in {@link #NON_RETRYABLE} skip both retry and fallback and propagate as-is -
     * an auth or config error should surface, not be masked by a silent switch to Ollama.
     */
    public ChatResponse call(Supplier<ChatResponse> primary, Supplier<ChatResponse> fallback) {
        currentFallback.set(fallback);
        try {
            return guard.get(primary);
        } finally {
            currentFallback.remove();
        }
    }
}
