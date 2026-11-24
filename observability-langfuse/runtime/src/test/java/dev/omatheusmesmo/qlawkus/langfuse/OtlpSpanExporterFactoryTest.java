package dev.omatheusmesmo.qlawkus.langfuse;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.quarkiverse.langfuse.runtime.otel.LangfuseSpanExporterConfig;
import io.vertx.core.Vertx;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The exporter is built against OpenTelemetry SDK internals, whose constructor signature moves between SDK
 * releases. Building one here fails on the first mismatch, which a native image would otherwise report only
 * when it links the class.
 */
class OtlpSpanExporterFactoryTest {

    @Test
    void buildsAnExporterAgainstTheOpenTelemetrySdkOnTheClasspath() {
        Vertx vertx = Vertx.vertx();
        try {
            LangfuseSpanExporterConfig config = new LangfuseSpanExporterConfig(URI.create("http://127.0.0.1:1"),
                    "/api/public/otel/v1/traces", Duration.ofSeconds(1), Map.of(), "application/x-protobuf",
                    MemoryMode.REUSABLE_DATA, options -> { }, vertx);

            SpanExporter exporter = new OtlpSpanExporterFactory().createExporter(config);

            assertNotNull(exporter);
            exporter.shutdown();
        } finally {
            vertx.close();
        }
    }
}
