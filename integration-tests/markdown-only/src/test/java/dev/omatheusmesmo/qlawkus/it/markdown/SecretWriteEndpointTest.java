package dev.omatheusmesmo.qlawkus.it.markdown;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the secret write endpoint over HTTP in a database-free build: writing a secret with no
 * {@code keytool}, listing aliases (never values), deleting, the {@code @Authenticated} gate, and the
 * 409 when no keystore password is configured. The decisive check reads the written {@code .p12} back
 * with the JDK {@link KeyStore} API and decodes the entry, proving the endpoint stores a secret
 * exactly the way {@code keytool -importpass} does, so the read side picks it up unchanged.
 *
 * <p>A {@link QuarkusTestProfile} points the keystore at a writable file under {@code target/} so the
 * committed read-side fixture ({@code secrets-test.p12}) is never touched.
 */
@QuarkusTest
@TestProfile(SecretWriteEndpointTest.WritableKeystore.class)
class SecretWriteEndpointTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";
  private static final String BASE = "/api/admin/secrets";
  private static final String STORE_PASSWORD = "write-test-password";
  private static final Path KEYSTORE = Path.of("target/markdown-only/secrets-write.p12");

  private static final String ALIAS = "qlawkus.test.written";
  private static final String VALUE = "written-secret-value";

  public static class WritableKeystore implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qlawkus.secrets.keystore-path", KEYSTORE.toString(),
          "qlawkus.secrets.keystore-password", STORE_PASSWORD);
    }
  }

  @BeforeEach
  void cleanKeystore() throws Exception {
    Files.deleteIfExists(KEYSTORE);
  }

  @Test
  void unauthenticated_isRejected() {
    given()
        .contentType("application/json")
        .body(Map.of("alias", ALIAS, "value", VALUE))
        .when().put(BASE)
        .then().statusCode(401);
  }

  @Test
  void blankValue_isRejected() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body(Map.of("alias", ALIAS, "value", ""))
        .when().put(BASE)
        .then().statusCode(400);
  }

  @Test
  void write_listsAlias_andStoresKeytoolCompatibleEntry() throws Exception {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body(Map.of("alias", ALIAS, "value", VALUE))
        .when().put(BASE)
        .then().statusCode(204);

    given().auth().preemptive().basic(USER, PASS)
        .when().get(BASE)
        .then().statusCode(200)
        .body("aliases", hasItem(ALIAS));

    assertEquals(VALUE, readBack(ALIAS));
  }

  @Test
  void delete_existing_thenMissing() {
    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body(Map.of("alias", ALIAS, "value", VALUE))
        .when().put(BASE)
        .then().statusCode(204);

    given().auth().preemptive().basic(USER, PASS)
        .when().delete(BASE + "?alias=" + ALIAS)
        .then().statusCode(204);

    given().auth().preemptive().basic(USER, PASS)
        .when().get(BASE)
        .then().statusCode(200)
        .body("aliases", not(hasItem(ALIAS)));

    given().auth().preemptive().basic(USER, PASS)
        .when().delete(BASE + "?alias=" + ALIAS)
        .then().statusCode(404);
  }

  private static String readBack(String alias) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream in = Files.newInputStream(KEYSTORE)) {
      keyStore.load(in, STORE_PASSWORD.toCharArray());
    }
    Key key = keyStore.getKey(alias, STORE_PASSWORD.toCharArray());
    assertNotNull(key, "alias should be present in the keystore");
    return new String(key.getEncoded(), StandardCharsets.UTF_8);
  }
}
