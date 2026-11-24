package dev.omatheusmesmo.qlawkus.store.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import dev.omatheusmesmo.qlawkus.task.TaskStatus;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The task and owner chat files are mapped by hand so no type needs native reflection. That holds
 * only while the mapping covers every component and keeps reading what the reflective mapper wrote.
 */
class PersistedStateJsonTest {

    private static final Instant WHEN = Instant.parse("2026-11-01T10:00:00.123Z");

    @Test
    void aScheduledTaskSurvivesTheRoundTripWithEveryField() throws Exception {
        ScheduledTask task = new ScheduledTask("task-1", "memory-1", new TaskDestination("discord", "42"),
                "water the plants", "0 0 9 * * ?", true, WHEN, TaskStatus.MISSED);

        assertEquals(task, ScheduledTaskJson.read(ScheduledTaskJson.write(task)));
    }

    @Test
    void theWrittenTaskCarriesEveryRecordComponent() throws Exception {
        ScheduledTask task = new ScheduledTask("t", "m", new TaskDestination("p", "c"), "x", "y", false, WHEN,
                TaskStatus.ACTIVE);

        String json = ScheduledTaskJson.write(task);

        for (RecordComponent component : ScheduledTask.class.getRecordComponents()) {
            assertTrue(json.contains("\"" + component.getName() + "\""), "dropped " + component.getName());
        }
        for (RecordComponent component : TaskDestination.class.getRecordComponents()) {
            assertTrue(json.contains("\"" + component.getName() + "\""), "dropped " + component.getName());
        }
    }

    @Test
    void aTaskFileWrittenByTheReflectiveMapperStillReads() throws Exception {
        String legacy = "{\"id\":\"task-1\",\"memoryId\":\"memory-1\","
                + "\"destination\":{\"providerId\":\"discord\",\"chatId\":\"42\"},\"task\":\"water the plants\","
                + "\"cron\":\"0 0 9 * * ?\",\"oneShot\":false,\"createdAt\":\"2026-11-01T10:00:00Z\","
                + "\"status\":\"ACTIVE\"}";

        ScheduledTask task = ScheduledTaskJson.read(legacy);

        assertEquals(new ScheduledTask("task-1", "memory-1", new TaskDestination("discord", "42"),
                "water the plants", "0 0 9 * * ?", false, Instant.parse("2026-11-01T10:00:00Z"),
                TaskStatus.ACTIVE), task);
    }

    @Test
    void aTaskWithAStatusNobodyKnowsIsRefused() {
        String json = "{\"id\":\"t\",\"memoryId\":\"m\",\"destination\":{\"providerId\":\"p\",\"chatId\":\"c\"},"
                + "\"task\":\"x\",\"cron\":\"y\",\"oneShot\":false,\"createdAt\":\"2026-11-01T10:00:00Z\","
                + "\"status\":\"SOMEDAY\"}";

        assertThrows(RuntimeException.class, () -> ScheduledTaskJson.read(json));
    }

    @Test
    void anOwnerChatSurvivesTheRoundTripWithEveryField() throws Exception {
        OwnerChat chat = new OwnerChat("telegram", "1678793311", WHEN);

        String json = OwnerChatJson.write(chat);

        assertEquals(chat, OwnerChatJson.read(json));
        for (RecordComponent component : OwnerChat.class.getRecordComponents()) {
            assertTrue(json.contains("\"" + component.getName() + "\""), "dropped " + component.getName());
        }
    }

    @Test
    void anOwnerChatFileWrittenByTheReflectiveMapperStillReads() throws Exception {
        String legacy = "{\"providerId\":\"discord\",\"chatId\":\"42\",\"lastSeen\":\"2026-11-01T10:00:00Z\"}";

        assertEquals(new OwnerChat("discord", "42", Instant.parse("2026-11-01T10:00:00Z")),
                OwnerChatJson.read(legacy));
    }

    @Test
    void theComponentListsAreWhatTheMappingWasWrittenFor() {
        assertEquals(8, ScheduledTask.class.getRecordComponents().length, "map the new ScheduledTask field");
        assertEquals(2, TaskDestination.class.getRecordComponents().length, "map the new TaskDestination field");
        assertEquals(3, OwnerChat.class.getRecordComponents().length, "map the new OwnerChat field");
        assertTrue(Arrays.stream(OwnerChat.class.getRecordComponents()).anyMatch(c -> c.getName().equals("lastSeen")));
    }
}
