package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The other half of the Google step: once the deployment supplies an OAuth client, the wizard offers
 * the authorize link. The link is the entire coupling to the Google module - this module bakes
 * {@code google-workspace} in its manifest without depending on the extension, so the console emits
 * the URL and the endpoint behind it exists only where the capability is really compiled in.
 */
@QuarkusTest
@TestProfile(GoogleAuthorizeStepTest.ConfiguredOAuthClient.class)
class GoogleAuthorizeStepTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";

  public static class ConfiguredOAuthClient implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qlawkus.google.auth.client-id", "an-oauth-client-id",
          "qlawkus.google.auth.client-secret", "an-oauth-client-secret");
    }
  }

  @Test
  void googleStep_offersTheAuthorizationLinkOnceTheClientIsConfigured() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/setup")
        .then().statusCode(200)
        .body(containsString("href=\"/api/google/oauth/start\""));
  }
}
