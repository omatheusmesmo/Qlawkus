package dev.omatheusmesmo.qlawkus.it.markdown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins that the packaged agent boots without the {@code io.opentelemetry.usage} warning, which an
 * operator reads as a defect in the agent and then has no way to act on.
 *
 * <p>The warning comes from Quarkus 3.x, not from this code: its Prometheus exemplar sampler and its
 * MicroProfile context propagation pass a possibly null context to {@code Span.fromContext}, which
 * OpenTelemetry API 1.62 reports. The {@code observability} extension silences that one logger until
 * the upstream fix ships, and this proves the silencing holds on the real artifact.
 */
@QuarkusIntegrationTest
class OpenTelemetryUsageWarningIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void thePackagedAgentBootsWithoutAnOpenTelemetryUsageWarning() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("target", "quarkus.log")).stream()
                .filter(line -> !line.isBlank())
                .toList();

        assertFalse(lines.isEmpty(), "the agent logged nothing at boot, so there is nothing to check");
        List<String> usageIssues = lines.stream()
                .filter(line -> "io.opentelemetry.usage".equals(loggerName(line)))
                .toList();
        assertEquals(List.of(), usageIssues,
                "an OpenTelemetry usage warning at boot points the operator at a defect they cannot fix");
    }

    private static String loggerName(String line) {
        try {
            JsonNode entry = JSON.readTree(line);
            return entry.path("loggerName").asText();
        } catch (IOException e) {
            return "";
        }
    }
}
