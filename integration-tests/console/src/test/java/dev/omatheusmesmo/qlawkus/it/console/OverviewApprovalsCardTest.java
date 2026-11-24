package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.approval.ApprovalGate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * The overview's approvals card tells the owner whether anything waits for a decision, without opening the
 * queue. With no tool named in {@code qlawkus.approval.tools} the gate is off, and the card says so.
 */
@QuarkusTest
class OverviewApprovalsCardTest {

  @Inject
  ApprovalGate gate;

  @Test
  void theOverviewLoadsTheCardAndPollsOnlyWhileTheTabIsVisible() {
    String page = get("/console");

    assertThat(page, containsString("hx-get=\"/console/overview/approvals\""));
    assertThat(page, containsString("every 10s [document.visibilityState=='visible']"));
  }

  @Test
  void theCardAsksForAuthentication() {
    given().get("/console/overview/approvals").then().statusCode(401);
  }

  @Test
  void theCardReportsHowManyRequestsWait() {
    assertThat(get("/console/overview/approvals"), containsString("data-pending=\"" + gate.pendingCount() + "\""));
  }

  @Test
  void aWaitingRequestIsCountedAndPointsToTheQueue() {
    int before = gate.pendingCount();
    gate.requestApproval("sendEmail", "{\"to\":\"card-owner@example.com\"}", "card-test",
        new AgentDeliveryContext.Snapshot(null, null, null));

    String card = get("/console/overview/approvals");

    assertThat(card, containsString("data-pending=\"" + (before + 1) + "\""));
    assertThat(card, containsString("waiting for you"));
    assertThat(card, containsString("href=\"/console/approvals\""));
  }

  @Test
  void withNoToolGatedTheCardSaysEveryToolRunsWithoutAsking() {
    String card = get("/console/overview/approvals");

    assertThat(card, containsString("data-gated=\"0\""));
    assertThat(card, containsString("No tool is gated"));
  }

  @Test
  void theCardNeverShowsTheRequestArguments() {
    gate.requestApproval("sendEmail", "{\"to\":\"private-argument@example.com\"}", "card-test",
        new AgentDeliveryContext.Snapshot(null, null, null));

    assertThat(get("/console/overview/approvals"), not(containsString("private-argument@example.com")));
  }

  private static String get(String path) {
    return given().auth().preemptive().basic("qlawkus", "qlawkus-test").get(path)
        .then().statusCode(200).extract().asString();
  }
}
