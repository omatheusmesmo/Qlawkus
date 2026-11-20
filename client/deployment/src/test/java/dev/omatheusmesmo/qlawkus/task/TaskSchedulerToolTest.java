package dev.omatheusmesmo.qlawkus.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the agent-facing contract of the scheduling tools: which destination a task gets from the
 * conversation it was made in, when a call is refused, and the warning every confirmation carries.
 */
@QuarkusTest
class TaskSchedulerToolTest {

    private static final String RECURRING = "0 0 9 * * ? 2099";
    private static final Pattern ID = Pattern.compile("qlawkus-task-[0-9a-f-]{36}");

    @Inject
    @QlawTool
    TaskSchedulerTool tool;

    @Inject
    DynamicTaskScheduler tasks;

    @Inject
    AgentDeliveryContext delivery;

    @Inject
    QlawToolProvider toolProvider;

    @BeforeEach
    void noConversationUnlessATestSetsOne() {
        delivery.clear();
    }

    @AfterEach
    void cancelEverythingItCreated() {
        delivery.clear();
        tasks.list().forEach(view -> tasks.cancel(view.task().id()));
    }

    @Test
    void theSchedulingToolsAreDiscoveredAtBuildTime() {
        Set<String> names = toolProvider.provideTools(null).tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());

        assertTrue(names.containsAll(List.of("scheduleTask", "listScheduledTasks", "cancelScheduledTask")), names.toString());
    }

    @Test
    void aMessagingConversationReusesItsChatWhenNoDestinationIsGiven() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("drink water", RECURRING, null, null, null);

        assertTrue(reply.startsWith("Scheduled task qlawkus-task-"), reply);
        assertTrue(reply.contains("telegram:42"), reply);
        assertEquals(1, tasks.list().size());
        assertEquals("telegram", tasks.list().get(0).task().destination().providerId());
        assertEquals("42", tasks.list().get(0).task().destination().chatId());
        assertEquals("telegram:42", tasks.list().get(0).task().memoryId());
    }

    @Test
    void anExplicitDestinationOverridesTheOriginForAnyChannel() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("call mum", RECURRING, null, "discord", "99");

        assertTrue(reply.contains("discord:99"), reply);
        assertFalse(reply.contains("telegram:42"), reply);
        assertEquals("discord", tasks.list().get(0).task().destination().providerId());
    }

    @Test
    void aRestConversationNeedsAnExplicitDestination() {
        delivery.set(ConversationId.REST, "rest", null);

        String reply = tool.scheduleTask("ping me", RECURRING, null, null, null);

        assertTrue(reply.startsWith("Error:"), reply);
        assertTrue(reply.contains("providerId") && reply.contains("chatId"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aRestConversationWithAnExplicitDestinationSchedulesIt() {
        delivery.set(ConversationId.REST, "rest", null);

        String reply = tool.scheduleTask("ping me", RECURRING, null, "telegram", "42");

        assertTrue(reply.startsWith("Scheduled task qlawkus-task-"), reply);
        assertEquals(ConversationId.REST, tasks.list().get(0).task().memoryId(),
                "the task still belongs to the REST conversation that created it");
        assertEquals("telegram", tasks.list().get(0).task().destination().providerId());
    }

    @Test
    void providerIdAndChatIdAreGivenTogether() {
        delivery.set(ConversationId.REST, "rest", null);

        assertTrue(tool.scheduleTask("ping", RECURRING, null, "telegram", null).startsWith("Error:"));
        assertTrue(tool.scheduleTask("ping", RECURRING, null, null, "42").startsWith("Error:"));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aMessagingConversationAlsoRefusesAHalfGivenOverride() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("ping", RECURRING, null, "discord", null);

        assertTrue(reply.startsWith("Error:"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void restIsNotAMessagingChannelAndCannotBeADestination() {
        delivery.set(ConversationId.REST, "rest", null);

        String reply = tool.scheduleTask("ping", RECURRING, null, "rest", "42");

        assertTrue(reply.startsWith("Error:"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aCallWithNoConversationIsRefused() {
        String reply = tool.scheduleTask("ping", RECURRING, null, "telegram", "42");

        assertTrue(reply.startsWith("Error:"), reply);
        assertTrue(reply.contains("conversation"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void exactlyOneOfCronOrInMinutesIsAccepted() {
        delivery.set("telegram:42", "telegram", "42");

        assertTrue(tool.scheduleTask("ping", RECURRING, 30, null, null).startsWith("Error:"));
        assertTrue(tool.scheduleTask("ping", null, null, null, null).startsWith("Error:"));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void inMinutesSchedulesAOneTimeTaskAtThatDelay() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("stretch", null, 120, null, null);

        assertTrue(reply.startsWith("Scheduled task qlawkus-task-"), reply);
        TaskView view = tasks.list().get(0);
        assertTrue(view.task().oneShot());
        long minutesAway = Duration.between(Instant.now(), view.nextFire()).toMinutes();
        assertTrue(minutesAway >= 118 && minutesAway <= 120, "about two hours away: " + minutesAway);
    }

    @Test
    void inMinutesOutsideTheAllowedRangeIsRefused() {
        delivery.set("telegram:42", "telegram", "42");

        assertTrue(tool.scheduleTask("stretch", null, 0, null, null).startsWith("Error:"));
        assertTrue(tool.scheduleTask("stretch", null, -5, null, null).startsWith("Error:"));
        assertTrue(tool.scheduleTask("stretch", null, 525_601, null, null).startsWith("Error:"));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aBlankOrOverlongTaskIsRefusedWithoutScheduling() {
        delivery.set("telegram:42", "telegram", "42");

        assertTrue(tool.scheduleTask("   ", RECURRING, null, null, null).startsWith("Error:"));
        String overlong = "x".repeat(DynamicTaskScheduler.MAX_TASK_LENGTH + 1);
        assertTrue(tool.scheduleTask(overlong, RECURRING, null, null, null).startsWith("Error:"));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void anInvalidCronIsRefusedWithoutScheduling() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("stretch", "every tuesday", null, null, null);

        assertTrue(reply.startsWith("Error:"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aFullTaskListIsRefusedWithTheCap() {
        delivery.set("telegram:42", "telegram", "42");
        for (int i = 0; i < DynamicTaskScheduler.MAX_TASKS; i++) {
            tasks.create("telegram:42", new TaskDestination("telegram", "42"), "task " + i, RECURRING);
        }

        String reply = tool.scheduleTask("one too many", RECURRING, null, null, null);

        assertTrue(reply.startsWith("Error:"), reply);
        assertTrue(reply.contains(String.valueOf(DynamicTaskScheduler.MAX_TASKS)), reply);
    }

    @Test
    void everyConfirmationWarnsThatSchedulesAreVolatile() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("stretch", RECURRING, null, null, null);

        assertTrue(reply.contains("lost on restart"), reply);
        assertTrue(reply.contains("redeploy"), reply);
        assertTrue(reply.contains("not caught up"), reply);
        assertTrue(reply.contains("best effort"), reply);
    }

    @Test
    void listShowsTheTasksOfTheAgentWithTheirNextRun() {
        delivery.set("telegram:42", "telegram", "42");
        tool.scheduleTask("stretch", RECURRING, null, null, null);

        String listing = tool.listScheduledTasks();

        assertTrue(listing.contains("stretch"), listing);
        assertTrue(listing.contains("telegram:42"), listing);
        assertTrue(listing.contains("recurring"), listing);
        assertTrue(listing.contains("lost on restart"), listing);
    }

    @Test
    void listSaysSoWhenThereIsNothingScheduled() {
        assertEquals("No scheduled tasks.", tool.listScheduledTasks().lines().findFirst().orElseThrow());
    }

    @Test
    void cancelRemovesATaskAndRefusesOneAlreadyGone() {
        delivery.set("telegram:42", "telegram", "42");
        String id = idOf(tool.scheduleTask("stretch", RECURRING, null, null, null));

        String cancelled = tool.cancelScheduledTask(id);
        String again = tool.cancelScheduledTask(id);

        assertTrue(cancelled.startsWith("Cancelled task " + id), cancelled);
        assertTrue(again.startsWith("Error:"), again);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void cancelRefusesAnIdThatIsNotAnAgentTask() {
        String reply = tool.cancelScheduledTask("not-a-task");

        assertTrue(reply.startsWith("Error:"), reply);
    }

    private static String idOf(String reply) {
        Matcher matcher = ID.matcher(reply);
        assertTrue(matcher.find(), "the reply names the task: " + reply);
        return matcher.group();
    }
}
