package dev.omatheusmesmo.qlawkus.metrics;

import io.smallrye.faulttolerance.api.CircuitBreakerState;

/**
 * Encodes a circuit breaker state as the number published by {@code qlawkus.model.circuit.state}.
 *
 * <p>The encoding lives in one place because a dashboard or alert reads these as bare numbers: a
 * panel says "state == 2 means open", so changing the mapping later silently reinterprets every
 * stored point. Ordered by severity so that "greater than zero" is a meaningful threshold.
 */
public final class CircuitStates {

    public static final double CLOSED = 0;
    public static final double HALF_OPEN = 1;
    public static final double OPEN = 2;

    private CircuitStates() {
    }

    public static double code(CircuitBreakerState state) {
        return switch (state) {
            case CLOSED -> CLOSED;
            case HALF_OPEN -> HALF_OPEN;
            case OPEN -> OPEN;
        };
    }
}
