package dev.omatheusmesmo.qlawkus.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the agent-facing contract of the scheduling tools: the wording the model reads, which destination a
 * task gets from the conversation it was made in, how blank optional arguments are read, and the plain-words
 * answers that say where a reply will go.
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

    @Inject
    ScheduledTaskStore store;

    @Inject
    AgentConfig config;

    @InjectMock
    OwnerChatDirectory owners;

    @BeforeEach
    void noConversationAndNoOwnerChatUnlessATestSetsOne() {
        delivery.clear();
        when(owners.lastChat(anyString())).thenReturn(Optional.empty());
        when(owners.mostRecent()).thenReturn(Optional.empty());
    }

    @AfterEach
    void cancelEverythingItCreated() {
        delivery.clear();
        tasks.list().forEach(view -> tasks.cancel(view.task().id()));
    }

    @Test
    void theSchedulingToolsAreDiscoveredAtBuildTime() {
        Set<String> names = toolSpecifications().stream().map(ToolSpecification::name)
                .collect(Collectors.toSet());

        assertTrue(names.containsAll(List.of("scheduleTask", "listScheduledTasks", "cancelScheduledTask")), names.toString());
    }

    @Test
    void theScheduleDescriptionCarriesTheWorkedCronExamplesAndTheTimezone() {
        String description = specification("scheduleTask").description();

        assertTrue(description.contains("0 0/5 * * * ? *"), description);
        assertTrue(description.contains("0 0 9 ? * MON-FRI *"), description);
        assertTrue(description.contains("inMinutes"), description);
        assertTrue(description.contains("qlawkus.agent.timezone"), description);
        assertTrue(description.contains("must not ask the owner"), description);
    }

    @Test
    void theScheduleToolTakesNoChatIdAndOnlyTheTaskIsRequired() {
        ToolSpecification spec = specification("scheduleTask");

        assertEquals(Set.of("task", "cron", "inMinutes", "providerId"), spec.parameters().properties().keySet());
        assertEquals(List.of("task"), spec.parameters().required());
    }

    @Test
    void everyOptionalParameterTellsTheModelToOmitItRatherThanSendEmpty() {
        Map<String, JsonSchemaElement> properties = specification("scheduleTask").parameters().properties();

        for (String optional : List.of("cron", "inMinutes", "providerId")) {
            assertTrue(descriptionOf(properties.get(optional)).contains("do not send an empty string"),
                    optional + ": " + descriptionOf(properties.get(optional)));
        }
    }

    @Test
    void noToolDescriptionAsksTheModelForAChatId() {
        for (ToolSpecification spec : toolSpecifications()) {
            assertFalse(spec.description().toLowerCase().contains("chat id")
                    || spec.description().contains("chatId"), spec.name() + ": " + spec.description());
            assertFalse(spec.parameters() != null && spec.parameters().properties().containsKey("chatId"), spec.name());
        }
    }

    @Test
    void theListAndCancelDescriptionsSayToConfirmRecurringTasksAndCancelOnlyListedIds() {
        assertTrue(specification("listScheduledTasks").description().contains("confirm a recurring task"),
                specification("listScheduledTasks").description());
        assertTrue(specification("cancelScheduledTask").description().contains("never make one up"),
                specification("cancelScheduledTask").description());
    }

    @Test
    void aBlankOptionalArgumentSentAsAnEmptyStringIsAbsentNotAConversionError() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = execute("scheduleTask",
                "{\"task\":\"stretch\",\"cron\":\"0 0 9 * * ? 2099\",\"inMinutes\":\"\",\"providerId\":\"\"}");

        assertTrue(reply.startsWith("Scheduled task qlawkus-task-"), reply);
        assertEquals(1, tasks.list().size());
    }

    @Test
    void aZeroInMinutesWithACronIsTheSameAsOmittingIt() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("stretch", RECURRING, "0", null);

        assertTrue(reply.startsWith("Scheduled task qlawkus-task-"), reply);
        assertFalse(tasks.list().get(0).task().oneShot());
    }

    @Test
    void aZeroInMinutesWithoutACronIsStillNoTiming() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("stretch", "", "0", null);

        assertTrue(reply.startsWith("Error:"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aMessagingConversationDeliversBackToItsChatWhenNoProviderIsGiven() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("drink water", RECURRING, null, null);

        assertTrue(reply.startsWith("Scheduled task qlawkus-task-"), reply);
        assertTrue(reply.contains("delivers to your Telegram chat"), reply);
        assertEquals(1, tasks.list().size());
        assertEquals(new TaskDestination("telegram", "42"), tasks.list().get(0).task().destination());
        assertEquals("telegram:42", tasks.list().get(0).task().memoryId());
    }

    @Test
    void theSameProviderNamedInAMessagingConversationStillMeansThatChat() {
        delivery.set("telegram:42", "telegram", "42");

        tool.scheduleTask("drink water", RECURRING, null, "Telegram");

        assertEquals(new TaskDestination("telegram", "42"), tasks.list().get(0).task().destination());
    }

    @Test
    void aProviderOtherThanTheOriginResolvesToTheOwnersLastChatOnIt() {
        delivery.set("telegram:42", "telegram", "42");
        when(owners.lastChat("discord")).thenReturn(Optional.of(new OwnerChat("discord", "99", Instant.now())));

        String reply = tool.scheduleTask("call mum", RECURRING, null, "discord");

        assertTrue(reply.contains("delivers to your Discord chat"), reply);
        assertEquals(new TaskDestination("discord", "99"), tasks.list().get(0).task().destination());
    }

    @Test
    void aRestCallWithNoProviderDeliversToTheOwnersMostRecentChat() {
        delivery.set(ConversationId.REST, "rest", null);
        when(owners.mostRecent()).thenReturn(Optional.of(new OwnerChat("discord", "99", Instant.now())));

        String reply = tool.scheduleTask("ping me", RECURRING, null, null);

        assertTrue(reply.startsWith("Scheduled task qlawkus-task-"), reply);
        assertTrue(reply.contains("delivers to your Discord chat"), reply);
        assertEquals(new TaskDestination("discord", "99"), tasks.list().get(0).task().destination());
        assertEquals(ConversationId.REST, tasks.list().get(0).task().memoryId(),
                "the task still belongs to the REST conversation that created it");
    }

    @Test
    void aRestCallWithNoChatYetSaysSoInPlainWordsAndAsksForNoId() {
        delivery.set(ConversationId.REST, "rest", null);

        String reply = tool.scheduleTask("ping me", RECURRING, null, null);

        assertTrue(reply.startsWith("Error:"), reply);
        assertTrue(reply.contains("no chat to deliver to yet"), reply);
        assertTrue(reply.contains("write to me on Telegram or Discord first"), reply);
        assertFalse(reply.contains("providerId") || reply.contains("chatId"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aRestCallNamingAProviderWithNoChatYetSaysSoInPlainWords() {
        delivery.set(ConversationId.REST, "rest", null);
        when(owners.lastChat("telegram")).thenReturn(Optional.empty());

        String reply = tool.scheduleTask("ping me", RECURRING, null, "telegram");

        assertTrue(reply.startsWith("Error:"), reply);
        assertTrue(reply.contains("no chat to deliver to on Telegram yet"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void restIsNotAMessagingChannelAndCannotBeADestination() {
        delivery.set(ConversationId.REST, "rest", null);

        String reply = tool.scheduleTask("ping", RECURRING, null, "rest");

        assertTrue(reply.startsWith("Error:"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aCallWithNoConversationIsRefused() {
        String reply = tool.scheduleTask("ping", RECURRING, null, "telegram");

        assertTrue(reply.startsWith("Error:"), reply);
        assertTrue(reply.contains("conversation"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void exactlyOneOfCronOrInMinutesIsAccepted() {
        delivery.set("telegram:42", "telegram", "42");

        assertTrue(tool.scheduleTask("ping", RECURRING, "30", null).startsWith("Error:"));
        assertTrue(tool.scheduleTask("ping", null, null, null).startsWith("Error:"));
        assertTrue(tool.scheduleTask("ping", "", "", null).startsWith("Error:"));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void inMinutesSchedulesAOneTimeTaskAtThatDelay() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("stretch", "", "120", null);

        assertTrue(reply.startsWith("Scheduled task qlawkus-task-"), reply);
        TaskView view = tasks.list().get(0);
        assertTrue(view.task().oneShot());
        long minutesAway = Duration.between(Instant.now(), view.nextFire()).toMinutes();
        assertTrue(minutesAway >= 118 && minutesAway <= 120, "about two hours away: " + minutesAway);
    }

    @Test
    void inMinutesThatIsNotAWholeNumberIsRefusedInWords() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("stretch", "", "soon", null);

        assertTrue(reply.startsWith("Error:"), reply);
        assertTrue(reply.contains("whole number of minutes"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void inMinutesOutsideTheAllowedRangeIsRefused() {
        delivery.set("telegram:42", "telegram", "42");

        assertTrue(tool.scheduleTask("stretch", "", "-5", null).startsWith("Error:"));
        assertTrue(tool.scheduleTask("stretch", "", "525601", null).startsWith("Error:"));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aBlankOrOverlongTaskIsRefusedWithoutScheduling() {
        delivery.set("telegram:42", "telegram", "42");

        assertTrue(tool.scheduleTask("   ", RECURRING, null, null).startsWith("Error:"));
        String overlong = "x".repeat(DynamicTaskScheduler.MAX_TASK_LENGTH + 1);
        assertTrue(tool.scheduleTask(overlong, RECURRING, null, null).startsWith("Error:"));
        assertEquals(0, tasks.list().size());
    }

    @Test
    void anInvalidCronIsRefusedWithoutScheduling() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("stretch", "every tuesday", null, null);

        assertTrue(reply.startsWith("Error:"), reply);
        assertEquals(0, tasks.list().size());
    }

    @Test
    void aFullTaskListIsRefusedWithTheCap() {
        delivery.set("telegram:42", "telegram", "42");
        for (int i = 0; i < DynamicTaskScheduler.MAX_TASKS; i++) {
            tasks.create("telegram:42", new TaskDestination("telegram", "42"), "task " + i, RECURRING);
        }

        String reply = tool.scheduleTask("one too many", RECURRING, null, null);

        assertTrue(reply.startsWith("Error:"), reply);
        assertTrue(reply.contains(String.valueOf(DynamicTaskScheduler.MAX_TASKS)), reply);
    }

    @Test
    void aConfirmationNamesTheTimezoneTheCronRunsIn() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("stretch", RECURRING, null, null);

        assertTrue(reply.contains(config.timezone()), reply);
    }

    @Test
    void everyConfirmationStatesThatSchedulesSurviveARestartAndHowAMissedOneIsHandled() {
        delivery.set("telegram:42", "telegram", "42");

        String reply = tool.scheduleTask("stretch", RECURRING, null, null);

        assertTrue(reply.contains("survive a restart"), reply);
        assertTrue(reply.contains("redeploy"), reply);
        assertTrue(reply.contains("reported as missed"), reply);
        assertTrue(reply.contains("not run"), reply);
        assertTrue(reply.contains("best effort"), reply);
        assertFalse(reply.contains("lost"), reply);
    }

    @Test
    void listShowsWhereEachReplyGoesInTheSameWordsAsTheConfirmation() {
        delivery.set("telegram:42", "telegram", "42");
        String reply = tool.scheduleTask("stretch", RECURRING, null, null);

        String listing = tool.listScheduledTasks();

        assertTrue(reply.contains("delivers to your Telegram chat"), reply);
        assertTrue(listing.contains("delivers to your Telegram chat"), listing);
        assertFalse(listing.contains("telegram:"), listing);
        assertTrue(listing.contains("stretch"), listing);
        assertTrue(listing.contains("recurring"), listing);
        assertTrue(listing.contains("survive a restart"), listing);
        assertFalse(listing.contains("lost"), listing);
    }

    @Test
    void aTaskThatWasDueWhileTheAgentWasDownIsListedAsMissedAndCanBeCancelled() {
        ScheduledTask due = new ScheduledTask("qlawkus-task-" + UUID.randomUUID(), "telegram:42",
                new TaskDestination("telegram", "42"), "pay the bill", "0 0 12 15 6 ? 2020", true,
                Instant.parse("2026-11-01T10:00:00Z"), TaskStatus.ACTIVE);
        store.save(due);
        tasks.restore();

        String listing = tool.listScheduledTasks();

        assertTrue(listing.contains(due.id()), listing);
        assertTrue(listing.contains("missed while the agent was down"), listing);
        assertTrue(listing.contains("cancel"), listing);
        assertTrue(tool.cancelScheduledTask(due.id()).startsWith("Cancelled task " + due.id()));
        assertFalse(tool.listScheduledTasks().contains(due.id()));
    }

    @Test
    void listSaysSoWhenThereIsNothingScheduled() {
        assertEquals("No scheduled tasks.", tool.listScheduledTasks().lines().findFirst().orElseThrow());
    }

    @Test
    void cancelRemovesATaskAndRefusesOneAlreadyGone() {
        delivery.set("telegram:42", "telegram", "42");
        String id = idOf(tool.scheduleTask("stretch", RECURRING, null, null));

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

    private List<ToolSpecification> toolSpecifications() {
        return toolProvider.provideTools(null).tools().keySet().stream().toList();
    }

    private ToolSpecification specification(String name) {
        return toolSpecifications().stream().filter(spec -> spec.name().equals(name)).findFirst().orElseThrow();
    }

    private String execute(String name, String arguments) {
        ToolExecutor executor = toolProvider.provideTools(null).tools().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals(name))
                .map(Map.Entry::getValue)
                .findFirst().orElseThrow();
        return executor.execute(ToolExecutionRequest.builder().name(name).arguments(arguments).build(),
                "task-tool-test");
    }

    private static String descriptionOf(JsonSchemaElement element) {
        if (element instanceof JsonStringSchema text) {
            return text.description();
        }
        if (element instanceof JsonIntegerSchema number) {
            return number.description();
        }
        throw new AssertionError("no description on " + element);
    }

    private static String idOf(String reply) {
        Matcher matcher = ID.matcher(reply);
        assertTrue(matcher.find(), "the reply names the task: " + reply);
        return matcher.group();
    }
}
