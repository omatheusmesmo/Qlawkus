package dev.omatheusmesmo.qlawkus.it.markdown;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * The redeploy report endpoint over HTTP: the only thing it may do is store a bounded, validated
 * record. Every rejection here is a way the endpoint could otherwise grow the history, smuggle text
 * into the console, or be mistaken for something that acts.
 */
@QuarkusTest
class RedeployEndpointTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";
  private static final String REDEPLOYS = "/api/admin/composition/redeploys";

  private static final String FAILED_BUILD = """
      {"startedAt":"2026-10-01T12:00:00Z","outcome":"failure","failedPhase":"build",
       "phases":{"fetch":120,"promote":4,"build":30000},"message":"build exited with status 3"}
      """;

  @Test
  void unauthenticated_isRejected() {
    given().when().get(REDEPLOYS).then().statusCode(401);
    given().contentType("application/json").body(FAILED_BUILD)
        .when().post(REDEPLOYS)
        .then().statusCode(401);
  }

  @Test
  void aValidReport_isRecordedAndReadableNewestFirst() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json").body(FAILED_BUILD)
        .when().post(REDEPLOYS)
        .then().statusCode(201)
        .body("outcome", equalTo("failure"))
        .body("recordedAt", notNullValue());

    given().auth().preemptive().basic(USER, PASS)
        .when().get(REDEPLOYS)
        .then().statusCode(200)
        .body("runs[0].outcome", equalTo("failure"))
        .body("runs[0].failedPhase", equalTo("build"))
        .body("runs[0].phases.build", equalTo(30000))
        .body("runs[0].message", equalTo("build exited with status 3"));
  }

  @Test
  void unknownFields_areNotStored() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("""
            {"startedAt":"2026-10-01T12:00:00Z","outcome":"success",
             "phases":{"fetch":1},"command":"rm -rf /"}
            """)
        .when().post(REDEPLOYS)
        .then().statusCode(201)
        .body("$", not(hasKey("command")));
  }

  @Test
  void anUnknownPhase_isRejected() {
    rejects("""
        {"startedAt":"2026-10-01T12:00:00Z","outcome":"failure","failedPhase":"deploy",
         "phases":{"fetch":1}}
        """);
    rejects("""
        {"startedAt":"2026-10-01T12:00:00Z","outcome":"success","phases":{"shell":1}}
        """);
  }

  @Test
  void anUnknownOutcome_isRejected() {
    rejects("""
        {"startedAt":"2026-10-01T12:00:00Z","outcome":"rolled-back","phases":{"fetch":1}}
        """);
  }

  @Test
  void theFailedPhase_mustMatchTheOutcome() {
    rejects("""
        {"startedAt":"2026-10-01T12:00:00Z","outcome":"failure","phases":{"fetch":1}}
        """);
    rejects("""
        {"startedAt":"2026-10-01T12:00:00Z","outcome":"success","failedPhase":"build",
         "phases":{"fetch":1}}
        """);
  }

  @Test
  void durations_areBounded() {
    rejects("""
        {"startedAt":"2026-10-01T12:00:00Z","outcome":"success","phases":{"fetch":-1}}
        """);
    rejects("""
        {"startedAt":"2026-10-01T12:00:00Z","outcome":"success","phases":{"fetch":86400001}}
        """);
    rejects("""
        {"startedAt":"2026-10-01T12:00:00Z","outcome":"success","phases":{}}
        """);
  }

  @Test
  void theMessage_isShortAndPlain() {
    rejects("""
        {"startedAt":"2026-10-01T12:00:00Z","outcome":"success","phases":{"fetch":1},
         "message":"%s"}
        """.formatted("x".repeat(201)));
    rejects("""
        {"startedAt":"2026-10-01T12:00:00Z","outcome":"success","phases":{"fetch":1},
         "message":"red \\u001b[31m alert"}
        """);
  }

  @Test
  void aMissingOrMalformedStart_isRejected() {
    rejects("""
        {"outcome":"success","phases":{"fetch":1}}
        """);
    rejects("""
        {"startedAt":"yesterday","outcome":"success","phases":{"fetch":1}}
        """);
  }

  private static void rejects(String body) {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json").body(body)
        .when().post(REDEPLOYS)
        .then().statusCode(400);
  }
}
