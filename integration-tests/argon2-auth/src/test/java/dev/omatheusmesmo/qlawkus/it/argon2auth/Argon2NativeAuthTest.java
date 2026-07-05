package dev.omatheusmesmo.qlawkus.it.argon2auth;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Argon2id admin auth against an {@code @Authenticated} endpoint. Run in JVM by this
 * class and against the GraalVM native image by {@link Argon2NativeAuthIT}, so the Bouncy Castle
 * Argon2id verification is proven to work in a native build (its reachability is guaranteed by direct
 * use of the low-level generator, and this asserts it actually runs). The admin password is
 * {@code argon2-native-pw}; the module enables argon2 statically so it is compiled into the image.
 */
@QuarkusTest
class Argon2NativeAuthTest {

  private static final String USER = "qlawkus";
  private static final String PASSWORD = "argon2-native-pw";
  private static final String ADMIN = "/api/admin/memory";

  @Test
  void argon2CredentialAuthenticates() {
    given().auth().preemptive().basic(USER, PASSWORD)
        .when().get(ADMIN)
        .then().statusCode(200);
  }

  @Test
  void wrongPasswordIsRejected() {
    given().auth().preemptive().basic(USER, "not-the-password")
        .when().get(ADMIN)
        .then().statusCode(401);
  }

  @Test
  void unauthenticatedIsRejected() {
    given()
        .when().get(ADMIN)
        .then().statusCode(401);
  }
}
