package dev.omatheusmesmo.qlawkus.heartbeat;

import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.task.DynamicTaskScheduler;
import dev.omatheusmesmo.qlawkus.task.TaskStatus;
import dev.omatheusmesmo.qlawkus.task.TaskView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

@ApplicationScoped
class HeartbeatObservationSource {

    private final DynamicTaskScheduler scheduler;
    private final FactStore facts;

    @Inject
    HeartbeatObservationSource(DynamicTaskScheduler scheduler, FactStore facts) {
        this.scheduler = scheduler;
        this.facts = facts;
    }

    HeartbeatObservations read() {
        var tasks = scheduler.list().stream().map(TaskView::task).toList();
        var problems = tasks.stream().filter(task -> task.status() == TaskStatus.MISSED || task.status() == TaskStatus.INVALID)
                .map(task -> task.id() + ":" + task.status()).sorted().toList();
        String fingerprint = sha256(problems.stream().collect(Collectors.joining("\n")));
        return new HeartbeatObservations(tasks.size(), problems.size(), fingerprint, !facts.listFactTexts(1).isEmpty());
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Unable to fingerprint heartbeat observations.");
        }
    }
}
