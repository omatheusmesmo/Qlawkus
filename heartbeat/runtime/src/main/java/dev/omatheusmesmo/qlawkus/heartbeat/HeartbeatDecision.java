package dev.omatheusmesmo.qlawkus.heartbeat;

import dev.langchain4j.model.output.TokenUsage;

record HeartbeatDecision(Action action, int tokens) {

    enum Action {
        NO_OP("Do nothing when no useful result is needed."),
        CHECK_SCHEDULES("Record counts of known scheduled tasks and problems."),
        CHECK_MEMORY("Record whether the owner has curated memory."),
        NOTIFY_SCHEDULES("Notify the owner about existing missed or invalid schedules, if any."),
        NOTIFY_MEMORY("Notify the owner if the curated memory store is empty.");

        final String description;

        Action(String description) {
            this.description = description;
        }
    }

    static HeartbeatDecision from(String choice, TokenUsage usage) {
        if (choice == null || choice.length() > 80) {
            throw new IllegalArgumentException("Invalid heartbeat decision.");
        }
        int tokens = usage == null || usage.totalTokenCount() == null ? -1 : usage.totalTokenCount();
        return new HeartbeatDecision(Action.valueOf(choice.strip()), tokens);
    }
}
