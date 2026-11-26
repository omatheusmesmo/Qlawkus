package dev.omatheusmesmo.qlawkus.routine;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.task.OwnerChat;
import dev.omatheusmesmo.qlawkus.task.OwnerChatDirectory;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RoutinesDisabledByDefaultTest {

    @Inject
    RoutineRunner routines;

    @Inject
    Scheduler scheduler;

    @Inject
    RoutinesConfig routinesConfig;

    @InjectMock
    AgentRunner agent;

    @InjectMock
    OwnerChatDirectory owners;

    @Test
    void aScheduledFiringDoesNothingUntilTheOwnerTurnsTheRoutineOn() {
        when(owners.mostRecent()).thenReturn(Optional.of(new OwnerChat("telegram", "42", Instant.EPOCH)));

        assertEquals(RoutineOutcome.DISABLED, routines.runScheduled(Routine.WEEKLY));
        assertEquals(RoutineOutcome.DISABLED, routines.runScheduled(Routine.MORNING));

        verify(agent, never()).chat(anyString(), anyString());
    }

    @Test
    void theWeeklyJobFiresOnFridayAtFourInTheAgentsZone() {
        ZonedDateTime next = nextFire("routine-weekly", routinesConfig.weekly().timeZone());

        assertEquals(DayOfWeek.FRIDAY, next.getDayOfWeek());
        assertEquals(LocalTime.of(16, 0), next.toLocalTime());
    }

    @Test
    void theMorningJobFiresOnMondayAtEight() {
        ZonedDateTime next = nextFire("routine-morning", routinesConfig.morning().timeZone());

        assertEquals(DayOfWeek.MONDAY, next.getDayOfWeek());
        assertEquals(LocalTime.of(8, 0), next.toLocalTime());
    }

    @Test
    void theOwnerCanTryARoutineNowEvenWhileItIsOff() {
        when(owners.mostRecent()).thenReturn(Optional.of(new OwnerChat("telegram", "42", Instant.EPOCH)));
        when(agent.chat(anyString(), anyString())).thenReturn("preview");

        given().auth().basic("qlawkus", "qlawkus-test").when().post("/api/admin/routines/weekly/run").then()
                .statusCode(200).body("outcome", equalTo("SENT"));
    }

    @Test
    void anUnknownRoutineIsNotFound() {
        given().auth().basic("qlawkus", "qlawkus-test").when().post("/api/admin/routines/nightly/run").then()
                .statusCode(404);
    }

    @Test
    void anonymousCallersAreRefused() {
        given().when().post("/api/admin/routines/weekly/run").then().statusCode(401);
    }

    private ZonedDateTime nextFire(String job, String zone) {
        var trigger = scheduler.getScheduledJob(job);
        assertNotNull(trigger, job + " must be registered even while the routine is off");
        return trigger.getNextFireTime().atZone(ZoneId.of(zone));
    }
}
