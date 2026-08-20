package dev.omatheusmesmo.qlawkus.it.cognition;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The counterpart to the markdown-only assertion that this check does not exist: here the pgvector
 * extension is on the classpath and Flyway really runs, so the check must be present and must pass.
 *
 * <p>Together the two tests pin the module-level placement rather than the behaviour of an {@code if}:
 * "migrations applied" is meaningless without a datasource, so it ships with the backend that has one
 * instead of being a conditional inside a shared check.
 */
@QuarkusTest
class MigrationReadinessTest {

    private static final String MIGRATIONS = "qlawkus-migrations";

    @Test
    void migrationCheckIsPresentWithThePgvectorExtension() {
        List<String> names = readiness().getList("checks.name");

        assertTrue(names.contains(MIGRATIONS),
                "a build with the Postgres backend must report on its schema; checks were " + names);
    }

    @Test
    void nothingIsPendingAfterMigrateAtStart() {
        JsonPath body = readiness();

        assertEquals("UP", body.getString("checks.find { it.name == '" + MIGRATIONS + "' }.status"));
        assertEquals(0, body.getInt("checks.find { it.name == '" + MIGRATIONS + "' }.data.pending"),
                "migrate-at-start ran, so the schema this build expects must already be applied");
    }

    private static JsonPath readiness() {
        return given().when().get("/q/health/ready").then().extract().jsonPath();
    }
}
