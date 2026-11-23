package dev.omatheusmesmo.qlawkus.heartbeat;

import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.util.Map;

@ApplicationScoped
class GuardedHeartbeatDecision {

    private final HeartbeatDecisionModel model;

    @Inject
    GuardedHeartbeatDecision(HeartbeatDecisionModel model) {
        this.model = model;
    }

    @Timeout(6000)
    @Bulkhead(1)
    @CircuitBreaker(requestVolumeThreshold = 3, failureRatio = 0.5, delay = 60000)
    HeartbeatDecision decide(Map<String, Object> fields) {
        return WorkloadContext.callAs("decision", () -> model.decide(fields));
    }
}
