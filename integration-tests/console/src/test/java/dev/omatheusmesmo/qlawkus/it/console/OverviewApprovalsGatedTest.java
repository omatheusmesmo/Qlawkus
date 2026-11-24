package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * With tools named in {@code qlawkus.approval.tools} the approvals card lists them by name, so the owner can
 * see what the gate covers.
 */
@QuarkusTest
@TestProfile(OverviewApprovalsGatedTest.GatedProfile.class)
class OverviewApprovalsGatedTest {

  public static class GatedProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("qlawkus.approval.tools", "sendEmail,deleteFile");
    }
  }

  @Test
  void theCardListsTheGatedToolsByName() {
    given().auth().preemptive().basic("qlawkus", "qlawkus-test")
        .when().get("/console/overview/approvals")
        .then().statusCode(200)
        .body(containsString("data-gated=\"2\""))
        .body(containsString("sendEmail"))
        .body(containsString("deleteFile"))
        .body(not(containsString("No tool is gated")));
  }
}
