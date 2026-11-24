package dev.omatheusmesmo.qlawkus.langfuse;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.otlp.internal.HttpExporter;
import io.opentelemetry.exporter.otlp.internal.HttpExporterBuilder;
import io.opentelemetry.sdk.common.InternalTelemetryVersion;
import io.opentelemetry.sdk.common.internal.ComponentId;
import io.opentelemetry.sdk.common.internal.StandardComponentId;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.quarkiverse.langfuse.runtime.otel.LangfuseSpanExporterConfig;
import io.quarkiverse.langfuse.runtime.otel.LangfuseSpanExporterFactory;
import io.quarkus.opentelemetry.runtime.exporter.otlp.sender.VertxHttpSender;
import io.quarkus.opentelemetry.runtime.exporter.otlp.tracing.VertxHttpSpanExporter;

/**
 * Builds the OTLP HTTP span exporter the way {@code quarkus-opentelemetry} does for OpenTelemetry SDK 1.67,
 * where {@code HttpExporter} takes a maximum request body size that the factory shipped with
 * quarkus-langfuse 0.9.1 does not pass. It reuses the Quarkus Vert.x sender, so no other sender is needed.
 */
final class OtlpSpanExporterFactory implements LangfuseSpanExporterFactory {

    @Override
    public SpanExporter createExporter(LangfuseSpanExporterConfig config) {
        var sender = new VertxHttpSender(
                config.baseUri(),
                config.signalPath(),
                false,
                config.timeout(),
                config.headers(),
                config.contentType(),
                config.clientOptionsCustomizer(),
                config.vertx());

        var httpExporter = new HttpExporter(
                ComponentId.generateLazy(StandardComponentId.ExporterType.OTLP_HTTP_SPAN_EXPORTER),
                sender,
                MeterProvider::noop,
                InternalTelemetryVersion.LATEST,
                config.baseUri(),
                false,
                HttpExporterBuilder.DEFAULT_MAX_REQUEST_BODY_SIZE);

        return new VertxHttpSpanExporter(httpExporter, config.memoryMode());
    }
}
