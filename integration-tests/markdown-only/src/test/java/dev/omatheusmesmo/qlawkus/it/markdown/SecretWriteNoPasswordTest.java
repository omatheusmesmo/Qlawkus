package dev.omatheusmesmo.qlawkus.it.markdown;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins the zero-configuration write: with no {@code qlawkus.secrets.keystore-password} configured, the
 * first secret written generates the store password into an owner-only file beside the keystore, so the
 * write succeeds instead of failing and the store is still encrypted. The read side stays lenient (an
 * absent keystore contributes nothing at boot).
 */
@QuarkusTest
@TestProfile(SecretWriteNoPasswordTest.NoPassword.class)
class SecretWriteNoPasswordTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";
  private static final String BASE = "/api/admin/secrets";

  public static class NoPassword implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qlawkus.secrets.keystore-path", "target/markdown-only/secrets-nopass.p12",
          "qlawkus.secrets.keystore-password", "");
    }
  }

  @Test
  void writeWithoutPassword_generatesOneBesideTheKeystore() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body(Map.of("alias", "qlawkus.test.nopass", "value", "irrelevant"))
        .when().put(BASE)
        .then().statusCode(204);

    assertTrue(Files.isRegularFile(Path.of("target/markdown-only/secrets-nopass.p12.password")));
  }
}
