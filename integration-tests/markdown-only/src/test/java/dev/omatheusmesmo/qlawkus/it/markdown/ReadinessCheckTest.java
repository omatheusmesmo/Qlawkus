package dev.omatheusmesmo.qlawkus.it.markdown;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that readiness reports on the agent rather than on nothing.
 *
 * <p>This module is the right host precisely because it has no datasource. The built-in checks that
 * ship with Quarkus are datasource-shaped, so a database-free distribution used to answer
 * {@code /q/health/ready} with UP and an empty check list - a probe that passes while the agent is
 * unable to serve. The k8s reference deployment gates its rollout on that endpoint, so "UP with no
 * checks" was a green light for a broken pod.
 */
@QuarkusTest
class ReadinessCheckTest {

    private static final String COGNITION = "qlawkus-cognition";
    private static final String MODEL = "qlawkus-model";
    private static final String MIGRATIONS = "qlawkus-migrations";

    @Test
    void readinessIsNotVacuous() {
        List<String> names = readiness().getList("checks.name");

        assertTrue(names.contains(COGNITION),
                "readiness must exercise the stores; reported checks were " + names);
        assertTrue(names.contains(MODEL),
                "readiness must report the model chain; reported checks were " + names);
    }

    @Test
    void cognitionCheckPassesWhenTheStoresAnswer() {
        assertEquals("UP", statusOf(COGNITION),
                "the markdown stores are readable in this build, so the check must pass");
    }

    @Test
    void modelCheckStaysUpWhileAFallbackExists() {
        JsonPath body = readiness();
        assertEquals("UP", statusOf(body, MODEL));
        assertEquals("CLOSED", body.getString("checks.find { it.name == '" + MODEL + "' }.data.chatCircuit"),
                "a boot that has called nothing must not report a tripped chat breaker");
        assertEquals("CLOSED", body.getString("checks.find { it.name == '" + MODEL + "' }.data.embeddingCircuit"),
                "a boot that has called nothing must not report a tripped embedding breaker");
    }

    @Test
    void livenessDoesNotDependOnTheModel() {
        List<String> names = given().when().get("/q/health/live").then()
                .statusCode(200).extract().jsonPath().getList("checks.name");

        assertTrue(names == null || !names.contains(MODEL),
                "a provider outage must not get the pod killed, only taken out of rotation");
    }

    @Test
    void migrationCheckIsAbsentWithoutThePgvectorExtension() {
        List<String> names = readiness().getList("checks.name");

        assertFalse(names.contains(MIGRATIONS),
                "a database-free build has no migrations, so the check must not exist here at all "
                        + "rather than pass vacuously; checks were " + names);
    }

    private static JsonPath readiness() {
        return given().when().get("/q/health/ready").then().extract().jsonPath();
    }

    private static String statusOf(String check) {
        return statusOf(readiness(), check);
    }

    private static String statusOf(JsonPath body, String check) {
        return body.getString("checks.find { it.name == '" + check + "' }.status");
    }
}
