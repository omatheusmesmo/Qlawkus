package dev.omatheusmesmo.qlawkus.it.console;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.store.ScheduledTaskStore;
import dev.omatheusmesmo.qlawkus.task.DynamicTaskScheduler;
import dev.omatheusmesmo.qlawkus.task.ScheduledTask;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import dev.omatheusmesmo.qlawkus.task.TaskStatus;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises the schedule page over HTTP: it lists the five background jobs sourced from the live
 * {@code Scheduler} and wires cron edits to the same runtime-toggles endpoint the config editor
 * uses, plus each job's existing manual-trigger endpoint.
 */
@QuarkusTest
@ConnectWireMock
class ScheduleConsoleTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";

  @Inject DynamicTaskScheduler tasks;
  @Inject ScheduledTaskStore store;
  WireMock wiremock;

  @Test
  void authenticatedConsoleChatCanScheduleOnceForARestoredDiscordDestination() {
    String chatEndpoint = "/v1/chat/completions";
    String task = "Send the console scheduling test reminder.";
    var destination = new TaskDestination("discord", "restored-fixture-chat");
    var legacy = new ScheduledTask(DynamicTaskScheduler.ID_PREFIX + UUID.randomUUID(),
        "discord:restored-fixture-chat", destination, "Legacy reminder",
        "0 0 9 * * ? 2099", false, Instant.now(), TaskStatus.ACTIVE);
    String arguments = "{\\\"task\\\":\\\"" + task + "\\\",\\\"inMinutes\\\":\\\"5\\\",\\\"providerId\\\":\\\"discord\\\"}";
    String vector = "[" + "0.01,".repeat(1023) + "0.01]";

    wiremock.resetToDefaultMappings();
    wiremock.resetRequests();
    wiremock.register(post(urlEqualTo("/v1/embeddings")).willReturn(okJson(
        "{\"data\":[{\"embedding\":" + vector + ",\"index\":0}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}")));
    wiremock.register(post(urlEqualTo(chatEndpoint)).atPriority(2).willReturn(okJson("""
        {"id":"call-reminder","object":"chat.completion","created":1,"model":"test",
         "choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant",
         "tool_calls":[{"id":"schedule-1","type":"function","function":{"name":"scheduleTask","arguments":"%s"}},
                       {"id":"schedule-2","type":"function","function":{"name":"scheduleTask","arguments":"%s"}}]}}]}
        """.formatted(arguments, arguments))));
    wiremock.register(post(urlEqualTo(chatEndpoint)).atPriority(1)
        .withRequestBody(matchingJsonPath("$.messages[?(@.role == 'tool')]"))
        .willReturn(okJson("""
            {"id":"finish-reminder","object":"chat.completion","created":1,"model":"test",
             "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"Reminder scheduled."}}]}
            """)));
    try {
      store.save(legacy);
      assertEquals(1, tasks.restore().restored());
      given().auth().preemptive().basic(USER, PASS).contentType("application/json")
          .body("{\"message\":\"Send me a simple reminder on Discord in five minutes.\"}")
          .post("/api/chat/sync").then().statusCode(200).body(containsString("Reminder scheduled."));

      var reminders = tasks.list().stream().filter(view -> view.task().task().equals(task)).toList();
      assertEquals(1, reminders.size());
      var reminder = reminders.getFirst();
      assertEquals(destination, reminder.task().destination());
      assertTrue(reminder.task().oneShot());
      wiremock.verifyThat(1, postRequestedFor(urlEqualTo(chatEndpoint))
          .withRequestBody(containing("Scheduled task " + reminder.task().id()))
          .withRequestBody(containing("delivers to your Discord chat")));
    } finally {
      tasks.list().stream().filter(view -> view.task().id().equals(legacy.id()) || view.task().task().equals(task))
          .forEach(view -> tasks.cancel(view.task().id()));
      wiremock.resetToDefaultMappings();
    }
  }

  @Test
  void scheduledTasksAppearWithTheirDestinationAndCanOnlyBeCancelledByAnAuthenticatedOwner() {
    var task = tasks.create("schedule-console", new TaskDestination("discord", "fixture-chat"),
        "Send the <agenda> summary", "0 0 6 * * ? *");
    String id = task.task().id();
    String path = "/api/admin/scheduled-tasks/" + id;
    try {
      given().auth().preemptive().basic(USER, PASS).when().get("/console/schedule").then()
          .statusCode(200)
          .body(containsString("Agent-scheduled tasks"))
          .body(containsString("Send the &lt;agenda&gt; summary"))
          .body(containsString("0 0 6 * * ? *"))
          .body(containsString("fixture-chat"))
          .body(containsString(tasks.zone().getId()))
          .body(containsString("hx-delete=\"" + path + "\""));

      given().when().delete(path).then().statusCode(401);
      given().auth().preemptive().basic(USER, PASS).when().delete("/api/admin/scheduled-tasks/memory-review")
          .then().statusCode(404);
      given().auth().preemptive().basic(USER, PASS).when().delete(path).then().statusCode(204);
      given().auth().preemptive().basic(USER, PASS).when().get("/console/schedule").then()
          .statusCode(200)
          .body(not(containsString("fixture-chat")))
          .body(containsString("No tasks are scheduled by the agent."))
          .body(containsString("qlawkus.memory-review.cron"));
    } finally {
      tasks.cancel(id);
    }
  }

  @Test
  void page_requiresAuthentication() {
    given().when().get("/console/schedule").then().statusCode(401);
  }

  @Test
  void page_listsAllFiveJobsWithCronAndTriggers() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/schedule")
        .then().statusCode(200)
        .body(containsString("<h1>Schedule</h1>"))
        .body(containsString("qlawkus.memory-review.cron"))
        .body(containsString("qlawkus.memory-curation.cron"))
        .body(containsString("qlawkus.consolidator.cron"))
        .body(containsString("qlawkus.skills.curation.cron"))
        .body(containsString("qlawkus.skills.lifecycle.cron"))
        .body(containsString("hx-post=\"/api/admin/memory/review\""))
        .body(containsString("hx-post=\"/api/admin/memory/curate\""))
        .body(containsString("hx-post=\"/api/admin/memory/consolidate\""))
        .body(containsString("hx-post=\"/api/admin/skills/curate\""))
        .body(containsString("hx-post=\"/api/admin/skills/lifecycle\""))
        .body(containsString("hx-put=\"/api/admin/runtime-toggles\""));
  }

  @Test
  void page_showsTheLastReportedRedeploy() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("""
            {"startedAt":"2026-10-01T12:00:00Z","outcome":"failure","failedPhase":"restart",
             "phases":{"fetch":120,"promote":4,"build":61500,"restart":2300},
             "message":"rollout <b>timed out</b>"}
            """)
        .when().post("/api/admin/composition/redeploys")
        .then().statusCode(201);

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/schedule")
        .then().statusCode(200)
        .body(containsString("Last redeploy"))
        .body(containsString("failure"))
        .body(containsString("failed in restart"))
        .body(containsString("61.5 s"))
        .body(containsString("120 ms"))
        .body(containsString("rollout &lt;b&gt;timed out&lt;/b&gt;"))
        .body(not(containsString("hx-post=\"/api/admin/composition/redeploys\"")));
  }

  @Test
  void sharedNav_linksTheSchedulePage() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/config")
        .then().statusCode(200)
        .body(containsString("href=\"/console/schedule\""));
  }

  @Test
  void cronEdit_roundTripsThroughTheRuntimeToggleFile() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/x-www-form-urlencoded")
        .formParam("property", "qlawkus.memory-review.cron")
        .formParam("value", "0 15 4 * * ?")
        .when()
        .put("/api/admin/runtime-toggles")
        .then()
        .statusCode(204);

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/api/admin/runtime-toggles")
        .then().statusCode(200)
        .body("'qlawkus.memory-review.cron'", equalTo("0 15 4 * * ?"));

    given().auth().preemptive().basic(USER, PASS)
        .when()
        .delete("/api/admin/runtime-toggles?property=qlawkus.memory-review.cron")
        .then()
        .statusCode(204);
  }
}
