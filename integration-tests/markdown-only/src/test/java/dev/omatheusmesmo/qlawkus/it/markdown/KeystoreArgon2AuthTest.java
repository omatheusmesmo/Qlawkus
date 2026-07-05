package dev.omatheusmesmo.qlawkus.it.markdown;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the #249 hardening end to end in a database-free build: the admin credential is an Argon2id
 * hash, sourced from the PKCS12 keystore, and it actually authenticates an HTTP Basic request against
 * an {@code @Authenticated} endpoint. The keystore fixture holds a real Argon2id PHC hash of
 * {@code keystore-admin-pw} under the alias {@code qlawkus.admin.password-hash}; the profile turns on
 * {@code Argon2AdminIdentityProvider} and turns the embedded properties realm off, so the argon2 path is
 * the only one exercised. Unlike the embedded realm, the hash resolves as an ordinary config property
 * the keystore overrides at ordinal 350, with no build-time-fixed expression indirection.
 */
@QuarkusTest
@TestProfile(KeystoreArgon2AuthTest.Argon2AdminAuth.class)
class KeystoreArgon2AuthTest {

  private static final String USER = "qlawkus";
  private static final String PASSWORD = "keystore-admin-pw";
  private static final String SECRETS = "/api/admin/secrets";

  public static class Argon2AdminAuth implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qlawkus.admin.argon2.enabled", "true",
          "quarkus.security.users.embedded.enabled", "false");
    }
  }

  @Test
  void argon2KeystoreCredentialAuthenticates() {
    given().auth().preemptive().basic(USER, PASSWORD)
        .when().get(SECRETS)
        .then().statusCode(200);
  }

  @Test
  void wrongPasswordIsRejected() {
    given().auth().preemptive().basic(USER, "not-the-password")
        .when().get(SECRETS)
        .then().statusCode(401);
  }

  @Test
  void unauthenticatedIsRejected() {
    given()
        .when().get(SECRETS)
        .then().statusCode(401);
  }
}
