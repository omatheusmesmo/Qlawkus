package dev.omatheusmesmo.qlawkus.store.markdown;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import dev.omatheusmesmo.qlawkus.task.TaskStatus;
import java.time.Instant;

/**
 * Maps a stored task field by field through Jackson's tree model, so no type is found by reflection
 * and a native image needs no registration for it. The layout is the one the reflective mapper
 * wrote: an ISO-8601 instant and the status by name.
 */
final class ScheduledTaskJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ScheduledTaskJson() {
    }

    static String write(ScheduledTask task) throws JsonProcessingException {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", task.id());
        node.put("memoryId", task.memoryId());
        TaskDestination destination = task.destination();
        if (destination == null) {
            node.putNull("destination");
        } else {
            ObjectNode destinationNode = node.putObject("destination");
            destinationNode.put("providerId", destination.providerId());
            destinationNode.put("chatId", destination.chatId());
        }
        node.put("task", task.task());
        node.put("cron", task.cron());
        node.put("oneShot", task.oneShot());
        node.put("createdAt", task.createdAt() == null ? null : task.createdAt().toString());
        node.put("status", task.status() == null ? null : task.status().name());
        return MAPPER.writeValueAsString(node);
    }

    static ScheduledTask read(String json) throws JsonProcessingException {
        JsonNode node = MAPPER.readTree(json);
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("not a task record");
        }
        JsonNode destination = node.path("destination");
        return new ScheduledTask(
                node.path("id").asText(null),
                node.path("memoryId").asText(null),
                destination.isObject()
                        ? new TaskDestination(destination.path("providerId").asText(null),
                                destination.path("chatId").asText(null))
                        : null,
                node.path("task").asText(null),
                node.path("cron").asText(null),
                node.path("oneShot").asBoolean(false),
                instant(node.path("createdAt")),
                status(node.path("status")));
    }

    private static Instant instant(JsonNode node) {
        return node.isTextual() ? Instant.parse(node.asText()) : null;
    }

    private static TaskStatus status(JsonNode node) {
        return node.isTextual() ? TaskStatus.valueOf(node.asText()) : null;
    }
}
