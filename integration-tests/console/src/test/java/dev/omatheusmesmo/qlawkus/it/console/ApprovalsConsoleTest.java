package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.approval.ApprovalRequest;
import dev.omatheusmesmo.qlawkus.approval.ApprovalService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * The owner's approval queue in the console. Renders the pending requests with the same admin endpoints the
 * REST API exposes, behind the same basic credential as every other console page.
 */
@QuarkusTest
class ApprovalsConsoleTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";

  @Inject
  ApprovalService approvals;

  @Test
  void approvalsPage_requiresAuthentication() {
    given().when().get("/console/approvals").then().statusCode(401);
  }

  @Test
  void approvalsPage_listsAPendingRequestWithApproveAndDenyControls() {
    approvals.requestApproval("sendEmail", "{\"to\":\"console-owner@example.com\"}", "console-test",
        new AgentDeliveryContext.Snapshot(null, null, null));
    String id = approvals.list().stream()
        .filter(request -> request.preview().contains("console-owner@example.com"))
        .findFirst()
        .map(ApprovalRequest::id)
        .orElseThrow();

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/approvals")
        .then().statusCode(200)
        .body(containsString("<h1>Approvals</h1>"))
        .body(containsString("console-owner@example.com"))
        .body(containsString("hx-post=\"/api/admin/approvals/" + id + "/approve\""))
        .body(containsString("hx-post=\"/api/admin/approvals/" + id + "/deny\""));
  }

  @Test
  void sharedNav_linksTheApprovalsPage() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/skills")
        .then().statusCode(200)
        .body(containsString("href=\"/console/approvals\""))
        .body(not(containsString("undefined")));
  }
}
