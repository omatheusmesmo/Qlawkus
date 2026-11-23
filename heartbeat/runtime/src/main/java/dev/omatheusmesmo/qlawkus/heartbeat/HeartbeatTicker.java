package dev.omatheusmesmo.qlawkus.heartbeat;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
class HeartbeatTicker {

    private final HeartbeatService heartbeat;

    @Inject
    HeartbeatTicker(HeartbeatService heartbeat) {
        this.heartbeat = heartbeat;
    }

    @Scheduled(identity = "agent-heartbeat", every = "10s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @RunOnVirtualThread
    void tick() {
        heartbeat.tick();
    }
}
