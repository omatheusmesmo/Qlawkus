package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;

import dev.omatheusmesmo.qlawkus.security.AdminSetupService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * The browser side of the first-run admin setup. With no admin password configured the console cannot
 * ask for credentials nobody has, so an anonymous visit lands on the setup page, which takes the
 * password once with the log token. Static assets stay reachable so the page is styled, and after the
 * password is set the console is an ordinary authenticated page again. The stateful flow runs last.
 */
@QuarkusTest
@TestProfile(FirstRunAdminSetupTest.FirstBoot.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FirstRunAdminSetupTest {

  private static final String PASSWORD = "a-long-first-run-password";
  private static final String PAGE = "/console/first-run";

  @Inject
  AdminSetupService setup;

  public static class FirstBoot implements QuarkusTestProfile {
    private static final Path DIRECTORY = createDirectory();

    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qlawkus.admin.argon2.enabled", "true",
          "quarkus.security.users.embedded.enabled", "false",
          "qlawkus.admin.password-hash", "",
          "qlawkus.secrets.keystore-path", DIRECTORY.resolve("secrets.p12").toString(),
          "qlawkus.secrets.keystore-password", "");
    }

    private static Path createDirectory() {
      try {
        Path root = Files.createDirectories(Path.of("target", "console"));
        return Files.createTempDirectory(root, "first-run");
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Test
  @Order(1)
  void anAnonymousVisitIsSentToTheSetupPage() {
    given().redirects().follow(false)
        .when().get("/console")
        .then().statusCode(303).header("Location", endsWith(PAGE));
    given().redirects().follow(false)
        .when().get("/console/memory")
        .then().statusCode(303).header("Location", endsWith(PAGE));
  }

  @Test
  @Order(2)
  void theSetupPageExplainsTheTokenAndHasTheForm() {
    given().when().get(PAGE)
        .then().statusCode(200)
        .body(containsString("setup token"))
        .body(containsString("name=\"token\""))
        .body(containsString("name=\"password\""))
        .body(containsString("name=\"confirm\""))
        .body(not(containsString(setup.token())));
  }

  @Test
  @Order(3)
  void staticAssetsAreNotRedirected() {
    given().redirects().follow(false)
        .when().get("/console/app.css")
        .then().statusCode(200);
  }

  @Test
  @Order(4)
  void aWrongTokenOrAMismatchedConfirmationShowsAnErrorAndSetsNothing() {
    given().contentType("application/x-www-form-urlencoded")
        .formParam("token", "not-the-token").formParam("password", PASSWORD).formParam("confirm", PASSWORD)
        .when().post(PAGE)
        .then().statusCode(200).body(containsString("setup token is wrong"));
    given().contentType("application/x-www-form-urlencoded")
        .formParam("token", setup.token()).formParam("password", PASSWORD).formParam("confirm", PASSWORD + "x")
        .when().post(PAGE)
        .then().statusCode(200).body(containsString("do not match"));
    given().contentType("application/x-www-form-urlencoded")
        .formParam("token", setup.token()).formParam("password", "short").formParam("confirm", "short")
        .when().post(PAGE)
        .then().statusCode(200).body(containsString("at least 12 characters"));
    given().redirects().follow(false).when().get("/console").then().statusCode(303);
  }

  @Test
  @Order(5)
  void theTokenSetsThePasswordAndTheConsoleThenNeedsIt() {
    given().contentType("application/x-www-form-urlencoded")
        .formParam("token", setup.token()).formParam("password", PASSWORD).formParam("confirm", PASSWORD)
        .when().post(PAGE)
        .then().statusCode(200).body(containsString("Password set")).body(containsString("qlawkus"));

    given().redirects().follow(false).when().get(PAGE).then().statusCode(303).header("Location", endsWith("/console"));
    given().redirects().follow(false).when().get("/console").then().statusCode(401);
    given().auth().preemptive().basic("qlawkus", PASSWORD).when().get("/console").then().statusCode(200);
  }
}
