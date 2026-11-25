package dev.omatheusmesmo.qlawkus.it.markdown;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.security.AdminSetupService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Proves the zero-configuration first boot end to end in a database-free build: with no admin hash and no
 * keystore password configured the app still boots, every protected endpoint refuses everyone, and the
 * first-run setup takes the password once, only with the log token, with no restart and no environment
 * file. The stateful flow runs last, in order, because completing setup closes it for the rest of the run.
 */
@QuarkusTest
@TestProfile(FirstBootAdminSetupTest.FirstBoot.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FirstBootAdminSetupTest {

  private static final String PASSWORD = "a-long-first-boot-password";
  private static final String PROTECTED = "/api/admin/secrets";
  private static final String SETUP = "/api/admin/setup";

  @Inject
  AdminSetupService setup;

  @ConfigProperty(name = "qlawkus.secrets.keystore-path")
  String keystorePath;

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
        Path root = Files.createDirectories(Path.of("target", "markdown-only"));
        return Files.createTempDirectory(root, "first-boot-admin");
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Test
  @Order(1)
  void theAppBootsInSetupModeAndSaysSoWithoutCredentials() {
    given().when().get(SETUP).then().statusCode(200).body("required", is(true));
  }

  @Test
  @Order(2)
  void nothingAuthenticatesWhileSetupIsOpen() {
    given().when().get(PROTECTED).then().statusCode(401);
    given().auth().preemptive().basic("qlawkus", PASSWORD).when().get(PROTECTED).then().statusCode(401);
    given().auth().preemptive().basic("qlawkus", "").when().get(PROTECTED).then().statusCode(401);
  }

  @Test
  @Order(3)
  void aWrongTokenAndABadPasswordAreRefused() {
    given().contentType("application/json")
        .body(Map.of("token", "not-the-token", "password", PASSWORD))
        .when().post(SETUP + "/password").then().statusCode(403);
    given().contentType("application/json")
        .body(Map.of("token", setup.token(), "password", "short"))
        .when().post(SETUP + "/password").then().statusCode(400);
    given().contentType("application/json")
        .body(Map.of("token", setup.token()))
        .when().post(SETUP + "/password").then().statusCode(400);
    given().when().get(SETUP).then().body("required", is(true));
  }

  @Test
  @Order(4)
  void theTokenSetsThePasswordOnceAndItWorksAtOnce() throws IOException {
    given().contentType("application/json")
        .body(Map.of("token", setup.token(), "password", PASSWORD))
        .when().post(SETUP + "/password").then().statusCode(204);

    given().auth().preemptive().basic("qlawkus", PASSWORD).when().get(PROTECTED).then().statusCode(200);
    given().auth().preemptive().basic("qlawkus", "not-the-password").when().get(PROTECTED).then().statusCode(401);
    given().when().get(SETUP).then().statusCode(200).body("required", is(false));
    given().contentType("application/json")
        .body(Map.of("token", setup.token(), "password", "another-long-password"))
        .when().post(SETUP + "/password").then().statusCode(409);

    Path keystore = Path.of(keystorePath);
    Path passwordFile = keystore.resolveSibling("secrets.p12.password");
    assertTrue(Files.isRegularFile(keystore));
    assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(passwordFile)));
  }
}
