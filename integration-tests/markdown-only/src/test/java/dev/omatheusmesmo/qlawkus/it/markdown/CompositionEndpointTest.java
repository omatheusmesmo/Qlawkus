package dev.omatheusmesmo.qlawkus.it.markdown;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the composition admin endpoint over HTTP in a database-free build: the {@code @Authenticated}
 * gate, staging a validated manifest, reading it back, rejecting a malformed one (400, never staged),
 * and discarding. Proves the seam is the API - the build/restart tooling reads the staged manifest over
 * HTTP, not a shared volume.
 */
@QuarkusTest
@TestProfile(CompositionEndpointTest.StateUnderTarget.class)
class CompositionEndpointTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";
  private static final String BASE = "/api/admin/composition";
  private static final Path STATE = Path.of("target/markdown-only/composition-state");

  private static final String VALID = """
      version: 1
      build-time:
        default: disabled
        except:
          - messaging.discord
      """;

  public static class StateUnderTarget implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("qlawkus.agent.state.root", STATE.toString());
    }
  }

  @BeforeEach
  void clearStaged() throws IOException {
    Files.deleteIfExists(STATE.resolve("agent.staged.yml"));
  }

  @Test
  void unauthenticated_isRejected() {
    given().when().get(BASE).then().statusCode(401);
    given().contentType("text/plain").body(VALID)
        .when().post(BASE + "/manifest")
        .then().statusCode(401);
  }

  @Test
  void stageValidManifest_thenReadItBack() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("text/plain").body(VALID)
        .when().post(BASE + "/manifest")
        .then().statusCode(200)
        .body("staged", equalTo(VALID));

    given().auth().preemptive().basic(USER, PASS)
        .when().get(BASE)
        .then().statusCode(200)
        .body("staged", equalTo(VALID));
  }

  @Test
  void exposesTheBakedActiveManifest() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get(BASE)
        .then().statusCode(200)
        .body("active", containsString("qlawkus.rt.baked-only"));
  }

  @Test
  void malformedManifest_isRejectedAndNotStaged() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("text/plain").body("version: 2\nbuild-time:\n  default: disabled\n")
        .when().post(BASE + "/manifest")
        .then().statusCode(400);

    given().auth().preemptive().basic(USER, PASS)
        .when().get(BASE)
        .then().statusCode(200)
        .body("staged", nullValue());
  }

  @Test
  void discardStaged_thenMissing() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("text/plain").body(VALID)
        .when().post(BASE + "/manifest")
        .then().statusCode(200);

    given().auth().preemptive().basic(USER, PASS)
        .when().delete(BASE + "/manifest")
        .then().statusCode(204);

    given().auth().preemptive().basic(USER, PASS)
        .when().delete(BASE + "/manifest")
        .then().statusCode(404);
  }
}
