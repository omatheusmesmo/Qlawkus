package dev.omatheusmesmo.qlawkus.it.markdown;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Pins which stores take the instance out of readiness when they cannot be written, and which only
 * report as degraded. The agent is single-instance, so DOWN does not move traffic elsewhere: it
 * means nobody answers. Only the stores a turn cannot run without may cause that.
 *
 * <p>Deliberately not part of {@link ReadinessCheckTest}, which {@link ReadinessCheckIT} re-runs
 * against the packaged app under the prod profile. There the roots resolve to the real
 * {@code ~/.qlawkus}, and a test that changes directory permissions must never reach it.
 */
@QuarkusTest
class StoreWritabilityReadinessTest {

    private static final String COGNITION = "qlawkus-cognition";

    @ConfigProperty(name = "qlawkus.agent.working-memory.root")
    String workingMemoryRoot;

    @ConfigProperty(name = "qlawkus.agent.facts.root")
    String factsRoot;

    @Test
    void aWorkingMemoryThatCannotBeWrittenTakesTheInstanceOutOfReadiness() throws IOException {
        JsonPath body = whileReadOnly(Path.of(workingMemoryRoot), 503);

        assertEquals("DOWN", cognition(body, "status"),
                "every turn appends to working memory, so the instance cannot serve a conversation");
        assertTrue(cognition(body, "data.workingMemory").contains(workingMemoryRoot),
                "the check must name the root an operator has to fix: " + body.prettify());
    }

    @Test
    void aFactRootThatCannotBeWrittenDegradesWithoutLeavingReadiness() throws IOException {
        JsonPath body = whileReadOnly(Path.of(factsRoot), 200);

        assertEquals("UP", cognition(body, "status"),
                "the agent still answers without remembering, and it is the only instance there is");
        assertEquals("facts", cognition(body, "data.degraded"));
        assertTrue(cognition(body, "data.facts").contains(factsRoot),
                "a degraded store must still say what is wrong: " + body.prettify());
    }

    /**
     * Reads readiness while {@code root} is read-only, restoring it before asserting so a failure
     * here cannot leave the rest of the suite on a broken volume.
     */
    private static JsonPath whileReadOnly(Path root, int expectedStatus) throws IOException {
        Files.createDirectories(root);
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("r-x------"));
        try {
            assumeFalse(Files.isWritable(root), "running as root, which ignores directory permissions");
            return given().when().get("/q/health/ready").then().statusCode(expectedStatus)
                    .extract().jsonPath();
        } finally {
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        }
    }

    private static String cognition(JsonPath body, String path) {
        return body.getString("checks.find { it.name == '" + COGNITION + "' }." + path);
    }
}
