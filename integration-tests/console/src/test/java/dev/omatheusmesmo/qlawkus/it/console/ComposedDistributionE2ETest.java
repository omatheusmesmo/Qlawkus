package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import io.quarkus.test.junit.QuarkusTest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.util.Collections;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * One boot carrying the composition manifest, the encrypted secret store and the console together,
 * with no Postgres and no datasource anywhere on the classpath. The individual pieces are pinned by
 * focused tests elsewhere ({@code CompositionGatingTest} and {@code KeystoreSecretTest} in the
 * markdown-only module, {@code ConsoleScaffoldTest} here); this asserts they hold up simultaneously
 * in a single composed distribution, which is the combination a real deployment ships.
 */
@QuarkusTest
class ComposedDistributionE2ETest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";

  private static CompositionManifest manifest;

  @ConfigProperty(name = "qlawkus.secrets.keystore-path")
  String keystorePath;

  @ConfigProperty(name = "qlawkus.secrets.keystore-password")
  String keystorePassword;

  @BeforeAll
  static void readBakedManifest() throws Exception {
    try (InputStream in = Thread.currentThread().getContextClassLoader()
        .getResourceAsStream(CompositionPaths.DEFAULT_MANIFEST)) {
      assertNotNull(in, "the composed distribution must bake a manifest at " + CompositionPaths.DEFAULT_MANIFEST);
      manifest = CompositionManifestParser.parse(in);
    }
  }

  @Test
  void manifest_selectsTheConsoleAndDeselectsPostgres() {
    assertTrue(manifest.buildTime().isEnabled("console"),
        "the manifest must select the console capability for this distribution");
    assertFalse(manifest.buildTime().isEnabled("cognition.pgvector"),
        "the manifest must deselect pgvector so the distribution stays database-free");

    assertTrue(classPresent("dev.omatheusmesmo.qlawkus.console.ConsoleResource"),
        "a selected capability must be composed in");
    assertFalse(classPresent("dev.omatheusmesmo.qlawkus.store.pg.PgFactStore"),
        "a deselected capability must not be composed in");
  }

  @Test
  void noDatabase_bootedWithTheConsoleMounted() {
    assertFalse(classPresent("io.agroal.api.AgroalDataSource"),
        "no JDBC datasource may be on the classpath");
    assertFalse(classPresent("io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore"),
        "no pgvector store may be on the classpath");
    assertFalse(classPresent("io.quarkus.devservices.postgresql.deployment.PostgresqlDevServicesProcessor"),
        "no Postgres Dev Service may be started");

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console")
        .then().statusCode(200)
        .body(containsString("Qlawkus"));
  }

  @Test
  void console_staysBehindTheSharedAuthenticatedGate() {
    given().when().get("/console").then().statusCode(401);
    given().when().get("/api/admin/secrets").then().statusCode(401);
  }

  @Test
  void secret_isOnboardedThroughTheAdminApiAndEncryptedOnDisk() throws Exception {
    String alias = "qlawkus.e2e.secret";
    String value = "e2e-secret-value";

    given().auth().preemptive().basic(USER, PASS)
        .contentType("application/json")
        .body("{\"alias\":\"" + alias + "\",\"value\":\"" + value + "\"}")
        .when().put("/api/admin/secrets")
        .then().statusCode(204);

    given().auth().preemptive().basic(USER, PASS)
        .when().get("/api/admin/secrets")
        .then().statusCode(200)
        .body("aliases", hasItem(alias))
        .body("aliases", not(contains(value)));

    // Read the store back with the plain JDK API: proves the file really is a password-protected
    // PKCS12 holding the value, i.e. the same shape keytool -importpass produces.
    Path file = Path.of(keystorePath);
    assertTrue(Files.isRegularFile(file), "the keystore must exist on disk at " + file);
    assertEquals(value, readSecret(file, keystorePassword.toCharArray(), alias));

    given().auth().preemptive().basic(USER, PASS)
        .when().delete("/api/admin/secrets?alias=" + alias)
        .then().statusCode(204);
  }

  @Test
  void consoleStatus_reportsTheSameManifestTheAppBootedWith() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/status")
        .then().statusCode(200)
        .body(containsString("default: disabled"))
        .body(containsString("console"));
  }

  private static String readSecret(Path file, char[] password, String alias) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream in = Files.newInputStream(file)) {
      keyStore.load(in, password);
    }
    assertTrue(Collections.list(keyStore.aliases()).contains(alias),
        "keystore should contain the onboarded alias");
    Key key = keyStore.getKey(alias, password);
    assertNotNull(key, "the alias should hold a readable secret key entry");
    return new String(key.getEncoded(), StandardCharsets.UTF_8);
  }

  private static boolean classPresent(String fqn) {
    try {
      Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
