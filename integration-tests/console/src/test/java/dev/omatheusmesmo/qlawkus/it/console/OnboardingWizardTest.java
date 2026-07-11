package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the onboarding wizard end to end over HTTP, database-free. It proves the wizard is only
 * an orchestrator of the existing admin surfaces: the LLM step lands a secret in the keystore, the
 * capability step stages a manifest through the composition service, and the Phase B steps appear
 * only for capabilities present in the baked manifest (this module bakes messaging.discord and
 * google-workspace so both follow-ups render).
 */
@QuarkusTest
class OnboardingWizardTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";
  private static final Path STAGED = Path.of("target/console/state/agent.staged.yml");
  private static final Path KEYSTORE = Path.of("target/console/secrets.p12");

  @BeforeEach
  void clearStaged() throws IOException {
    Files.deleteIfExists(STAGED);
  }

  /**
   * The LLM/token steps write real secrets into the throwaway keystore. Remove it afterwards so the
   * next run boots unconfigured again (the keystore outranks application.properties, so a leftover
   * key would otherwise flip the first-run detection and hide the setup banner).
   */
  @AfterAll
  static void removeTestKeystore() throws IOException {
    Files.deleteIfExists(KEYSTORE);
  }

  @Test
  void setupPage_requiresAuthentication() {
    given().when().get("/console/setup").then().statusCode(401);
    given().when().post("/console/setup/llm").then().statusCode(401);
  }

  @Test
  void console_showsSetupBannerWhenUnconfigured() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console")
        .then().statusCode(200)
        .body(containsString("setup-banner"))
        .body(containsString("/console/setup"));
  }

  @Test
  void setupPage_rendersPhaseAAndPhaseB() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/setup")
        .then().statusCode(200)
        .body(containsString("First-run setup"))
        .body(containsString("/console/setup/llm"))
        .body(containsString("name=\"capability\""))
        .body(containsString("Google is built in"))
        .body(containsString("/console/setup/messaging"));
  }

  @Test
  void llmStep_writesTheApiKeySecret() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/x-www-form-urlencoded")
        .formParam("apiKey", "real-secret-key")
        .formParam("baseUrl", "https://example.test/v1")
        .when().post("/console/setup/llm")
        .then().statusCode(200)
        .body(containsString("saved"));

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/api/admin/secrets")
        .then().statusCode(200)
        .body(containsString("langchain4j.openai"));
  }

  @Test
  void capabilitiesStep_stagesManifestAlwaysKeepingConsole() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/x-www-form-urlencoded")
        .formParam("capability", "brag")
        .when().post("/console/setup/capabilities")
        .then().statusCode(200)
        .body(containsString("staged"));

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/api/admin/composition")
        .then().statusCode(200)
        .body(containsString("brag"))
        .body(containsString("console"));
  }

  @Test
  void messagingStep_writesTheBotTokenSecret() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/x-www-form-urlencoded")
        .formParam("property", "qlawkus.messaging.discord.bot-token")
        .formParam("token", "a-real-bot-token")
        .when().post("/console/setup/messaging")
        .then().statusCode(200)
        .body(containsString("saved"));

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/api/admin/secrets")
        .then().statusCode(200)
        .body(containsString("messaging.discord.bot-token"));
  }
}
