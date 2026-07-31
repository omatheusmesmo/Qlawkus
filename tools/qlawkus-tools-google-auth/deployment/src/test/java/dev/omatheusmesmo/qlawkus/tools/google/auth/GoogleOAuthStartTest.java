package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The browser entry point into the OAuth flow, which the console links to so Google can be authorized
 * without going through chat. What matters here is that it redirects to a consent URL carrying a
 * fresh single-use state - the same contract {@code startGoogleAuthorization} produces, since both go
 * through {@link GoogleAuthorizationUrls}.
 */
@QuarkusTest
class GoogleOAuthStartTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";

  @Test
  void start_requiresAuthentication() {
    given().redirects().follow(false)
        .when().get("/api/google/oauth/start")
        .then().statusCode(401);
  }

  @Test
  void start_redirectsToGoogleConsentWithStateAndConfiguredRedirectUri() {
    Response response = given().auth().preemptive().basic(USER, PASS)
        .redirects().follow(false)
        .when().get("/api/google/oauth/start")
        .then().statusCode(303)
        .extract().response();

    String location = response.getHeader("Location");
    assertNotNull(location, "expected a redirect to Google");
    assertTrue(location.startsWith("https://accounts.google.com/o/oauth2/v2/auth"), location);
    assertTrue(location.contains("client_id=test-client-id"), location);
    assertTrue(location.contains("response_type=code"), location);
    assertTrue(location.contains("state="), location);
    assertTrue(location.contains(
        "redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fgoogle%2Foauth%2Fcallback"), location);
  }

  /** Each kickoff must mint its own state, or a second tab would invalidate the first one's callback. */
  @Test
  void start_issuesAFreshStateEachTime() {
    String first = stateOf(startAuthorized());
    String second = stateOf(startAuthorized());

    assertNotEquals(first, second);
  }

  /** The state the endpoint issues is what the callback consumes; an unknown one must be refused. */
  @Test
  void callback_rejectsAStateThatWasNeverIssued() {
    given().redirects().follow(false)
        .when().get("/api/google/oauth/callback?code=some-code&state=not-a-real-state")
        .then().statusCode(400);
  }

  @Test
  void callback_rejectsAMissingCode() {
    given().redirects().follow(false)
        .when().get("/api/google/oauth/callback?state=whatever")
        .then().statusCode(400);
  }

  private String startAuthorized() {
    return given().auth().preemptive().basic(USER, PASS)
        .redirects().follow(false)
        .when().get("/api/google/oauth/start")
        .then().statusCode(303)
        .extract().header("Location");
  }

  private String stateOf(String location) {
    int at = location.indexOf("state=");
    assertNotEquals(-1, at, location);
    String state = location.substring(at + "state=".length());
    int amp = state.indexOf('&');
    String value = amp == -1 ? state : state.substring(0, amp);
    assertFalse(value.isBlank(), "state must not be blank");
    return value;
  }
}
