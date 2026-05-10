package dev.omatheusmesmo.qlawkus.model;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class CircuitBreaker {

    enum State { CLOSED, OPEN, HALF_OPEN }

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private volatile Instant openedAt = Instant.MIN;

    private final Duration resetTimeout;

    @Inject
    public CircuitBreaker(ModelFallbackConfig config) {
        this.resetTimeout = config.resetTimeout();
    }

    public boolean isOpen() {
        State current = state.get();
        if (current == State.OPEN) {
            if (Instant.now().isAfter(openedAt.plus(resetTimeout))) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    Log.info("Circuit breaker transitioning to HALF_OPEN");
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public boolean isHalfOpen() {
        return state.get() == State.HALF_OPEN;
    }

    public void recordFailure() {
        State previous = state.getAndSet(State.OPEN);
        openedAt = Instant.now();
        if (previous != State.OPEN) {
            Log.infof("Circuit breaker OPEN — all calls will use fallback for %ds", resetTimeout.toSeconds());
        }
    }

    public void recordSuccess() {
        State previous = state.getAndSet(State.CLOSED);
        if (previous != State.CLOSED) {
            Log.info("Circuit breaker CLOSED — primary provider restored");
        }
    }

    public State currentState() {
        return state.get();
    }

    public void forceOpen() {
        state.set(State.OPEN);
        openedAt = Instant.now();
    }
}
