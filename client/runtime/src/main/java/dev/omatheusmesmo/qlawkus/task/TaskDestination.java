package dev.omatheusmesmo.qlawkus.task;

public record TaskDestination(String providerId, String chatId) {

    public TaskDestination {
        DynamicTaskScheduler.requireText(providerId, "providerId", DynamicTaskScheduler.MAX_DESTINATION_LENGTH);
        DynamicTaskScheduler.requireText(chatId, "chatId", DynamicTaskScheduler.MAX_DESTINATION_LENGTH);
    }
}
