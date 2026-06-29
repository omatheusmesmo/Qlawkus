package dev.omatheusmesmo.qlawkus.it.markdown;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins the DX guard: writing a secret with no {@code qlawkus.secrets.keystore-password} configured
 * returns {@code 409 Conflict} with a clear message instead of a 500, since the store cannot be
 * unlocked. The read side stays lenient (an absent/locked keystore contributes nothing at boot), so
 * the app still starts; only the write attempt is refused.
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
  void writeWithoutPassword_returnsConflict() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body(Map.of("alias", "qlawkus.test.nopass", "value", "irrelevant"))
        .when().put(BASE)
        .then().statusCode(409);
  }
}
