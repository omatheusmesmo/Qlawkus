package dev.omatheusmesmo.qlawkus.it.markdown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins that the packaged agent logs JSON, one object per line, when the {@code observability}
 * capability is selected. This runs the real artifact under the prod profile, which is the only
 * place the setting applies: dev and test keep the readable format.
 *
 * <p>JSON is what turns container logs into something an aggregator can query, and it carries the
 * MDC, which is where the trace id lives once tracing is on (pinned by {@code LogTraceCorrelationTest}).
 *
 * <p>The integration-test launcher captures the app through the file handler it switches on, not the
 * console a container would collect. Both formatters are switched by the same extension, so this
 * proves the packaged agent writes JSON; it cannot tell the two handlers apart.
 */
@QuarkusIntegrationTest
class JsonLogIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void everyLineThePackagedAgentLogsIsAJsonObject() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("target", "quarkus.log")).stream()
                .filter(line -> !line.isBlank())
                .toList();

        assertFalse(lines.isEmpty(), "the agent logged nothing at boot, so there is nothing to check");
        for (String line : lines) {
            JsonNode entry;
            try {
                entry = JSON.readTree(line);
            } catch (IOException e) {
                fail("a log line is not JSON, so an aggregator cannot parse it: " + line);
                return;
            }
            assertTrue(entry.has("message") && entry.has("level") && entry.has("loggerName"),
                    "a JSON entry without message, level and logger is not queryable: " + line);
        }
    }
}
