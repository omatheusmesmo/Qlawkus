package dev.omatheusmesmo.qlawkus.heartbeat;

import java.util.Map;

record HeartbeatObservations(int tasks, int taskIssues, String taskFingerprint, boolean hasCuratedMemory) {

    Map<String, Object> decisionFields(String prompt, boolean notificationAvailable) {
        return Map.of("ownerInstructions", prompt, "scheduledTaskCount", tasks, "scheduledTaskProblems", taskIssues,
                "hasCuratedMemory", hasCuratedMemory, "notificationAvailable", notificationAvailable);
    }
}
