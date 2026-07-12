package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Exercises the full configuration editor: the {@code /console/config} page rendered from the
 * bundled {@code quarkus-config-doc} metadata, and the two admin write paths it drives - the
 * {@code RUN_TIME} runtime-toggle override and the {@code BUILD_TIME}/{@code BUILD_AND_RUN_TIME_FIXED}
 * staged overrides - database-free.
 */
@QuarkusTest
class ConfigEditorTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";
  private static final String RUN_TIME_PROPERTY = "qlawkus.memory-curation.enabled";
  private static final String BUILD_TIME_PROPERTY = "qlawkus.composition.manifest";

  @Test
  void page_requiresAuthentication() {
    given().when().get("/console/config").then().statusCode(401);
  }

  @Test
  void page_rendersScopedPropertiesGroupedByRoot() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/config")
        .then().statusCode(200)
        .body(containsString("<h1>Config</h1>"))
        .body(containsString(RUN_TIME_PROPERTY))
        .body(containsString("RUN_TIME"))
        .body(containsString(BUILD_TIME_PROPERTY))
        // secret properties are never in scope, so a secret never even reaches the page
        .body(not(containsString("api-key")));
  }

  @Test
  void sharedNav_linksTheConfigPage() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/memory")
        .then().statusCode(200)
        .body(containsString("href=\"/console/config\""));
  }

  @Test
  void runtimeToggle_setAndDelete_roundTripThroughTheOverrideFile() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("{\"property\":\"%s\",\"value\":\"false\"}".formatted(RUN_TIME_PROPERTY))
        .when().put("/api/admin/runtime-toggles")
        .then().statusCode(204);

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/api/admin/runtime-toggles")
        .then().statusCode(200)
        // the key is a single flat property name containing dots, not a nested JSON path,
        // so it must be quoted or REST-assured's JsonPath tries to navigate into it
        .body("'" + RUN_TIME_PROPERTY + "'", org.hamcrest.Matchers.equalTo("false"));

    given().auth().preemptive().basic(USER, PASS)
        .when().delete("/api/admin/runtime-toggles?property=" + RUN_TIME_PROPERTY)
        .then().statusCode(204);
  }

  @Test
  void runtimeToggle_rejectsASecretProperty() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("{\"property\":\"qlawkus.admin.password-hash\",\"value\":\"x\"}")
        .when().put("/api/admin/runtime-toggles")
        .then().statusCode(400)
        .body("error", containsString("secret"));
  }

  @Test
  void configOverrides_stageAndDiscard_singleProperty() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("{\"property\":\"%s\",\"value\":\"qlawkus/agent.yml\"}".formatted(BUILD_TIME_PROPERTY))
        .when().put("/api/admin/config-overrides")
        .then().statusCode(200)
        .body("staged", containsString(BUILD_TIME_PROPERTY));

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/api/admin/config-overrides")
        .then().statusCode(200)
        .body("staged", containsString(BUILD_TIME_PROPERTY));

    given().auth().preemptive().basic(USER, PASS)
        .when().delete("/api/admin/config-overrides?property=" + BUILD_TIME_PROPERTY)
        .then().statusCode(204);

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/api/admin/config-overrides")
        .then().statusCode(200)
        .body("staged", org.hamcrest.Matchers.nullValue());
  }

  @Test
  void configOverrides_rejectsARunTimeProperty() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("{\"property\":\"%s\",\"value\":\"false\"}".formatted(RUN_TIME_PROPERTY))
        .when().put("/api/admin/config-overrides")
        .then().statusCode(400)
        .body("error", containsString("RUN_TIME"));
  }

  @Test
  void configOverrides_rejectsASecretProperty() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("{\"property\":\"qlawkus.admin.password-hash\",\"value\":\"x\"}")
        .when().put("/api/admin/config-overrides")
        .then().statusCode(400)
        .body("error", containsString("secret"));
  }

  @Test
  void configOverrides_rejectsAnUndocumentedProperty() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("{\"property\":\"qlawkus.does-not-exist.nope\",\"value\":\"x\"}")
        .when().put("/api/admin/config-overrides")
        .then().statusCode(400)
        .body("error", containsString("not a documented"));
  }
}
