package dev.omatheusmesmo.qlawkus.it.markdown;

import dev.omatheusmesmo.qlawkus.config.metadata.ConfigMetadataIndex;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the qlawkus-observability extension contributes beyond the meters TelemetryTest already pins.
 *
 * <p>This module is the right host because it takes the extension the way a consumer does - by
 * selecting the capability - rather than declaring a registry of its own.
 */
@QuarkusTest
class ObservabilityCapabilityTest {

    @Inject
    Config config;

    @Inject
    ConfigMetadataIndex configMetadata;

    @Test
    void openTelemetryShipsDisabled() {
        assertTrue(config.getValue("quarkus.otel.sdk.disabled", Boolean.class),
                "upstream enables the SDK with the traces exporter defaulting to OTLP on localhost:4317, "
                        + "so selecting this capability with no collector listening would log export "
                        + "failures in a loop for someone who only wanted metrics");
    }

    @Test
    void theExporterPropertiesReachTheConfigEditor() {
        assertTrue(configMetadata.find("quarkus.otel.exporter.otlp.endpoint").isPresent(),
                "turning tracing on is a runtime toggle, so the endpoint has to be editable from the "
                        + "console - which means this extension's allowlist entries must be merged with "
                        + "the client's rather than losing to them");
        assertTrue(configMetadata.find("quarkus.otel.sdk.disabled").isPresent(),
                "the property that turns the SDK on must be reachable the same way");
    }
}
