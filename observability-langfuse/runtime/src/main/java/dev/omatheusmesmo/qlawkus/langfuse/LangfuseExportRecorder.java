package dev.omatheusmesmo.qlawkus.langfuse;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.quarkiverse.langfuse.config.LangfuseConfig;
import io.quarkiverse.langfuse.runtime.otel.LangfuseSpanExporterFactoryQuarkus3;
import io.quarkiverse.langfuse.runtime.otel.LangfuseSpanProcessor;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.vertx.core.Vertx;
import org.jboss.logmanager.MDC;

@Recorder
public class LangfuseExportRecorder {

    private static final String UNCONFIGURED_KEY = "unconfigured";
    private static final String UNCONFIGURED_URL = "http://127.0.0.1:1";

    private final RuntimeValue<LangfuseExportConfig> export;
    private final RuntimeValue<LangfuseConfig> langfuse;

    public LangfuseExportRecorder(RuntimeValue<LangfuseExportConfig> export, RuntimeValue<LangfuseConfig> langfuse) {
        this.export = export;
        this.langfuse = langfuse;
    }

    public Function<SyntheticCreationalContext<SpanProcessor>, SpanProcessor> create(
            Optional<RuntimeValue<Boolean>> sdkEnabled, Supplier<Vertx> vertx) {
        return context -> {
            LangfuseConfig config = langfuse.getValue();
            if (!sdkEnabled.map(RuntimeValue::getValue).orElse(false) || !export.getValue().enabled()
                    || !configured(config)) {
                return LangfuseSpanProcessor.noop();
            }
            var factory = new LangfuseSpanExporterFactoryQuarkus3();
            return new LangfuseSpanProcessor(config, vertx.get(), context.getInjectedReference(TlsConfigurationRegistry.class),
                    exporter -> new LangfuseMetadataExporter(factory.createExporter(exporter))) {
                @Override
                public void onStart(Context parentContext, ReadWriteSpan span) {
                    String conversation = MDC.get("conversation");
                    if (conversation == null || conversation.isBlank()) {
                        conversation = Baggage.fromContext(parentContext).getEntryValue("gen_ai.conversation.id");
                    }
                    if (conversation != null && !conversation.isBlank()) {
                        span.setAttribute(LangfuseMetadataExporter.TURN_SESSION, LangfuseMetadataExporter.sessionId(conversation));
                    }
                    String workload = MDC.get("workload");
                    span.setAttribute(LangfuseMetadataExporter.WORKLOAD, workload != null ? workload : WorkloadContext.current());
                }
            };
        };
    }

    static boolean configured(LangfuseConfig config) {
        if (UNCONFIGURED_URL.equals(config.baseUrl()) || !isKeySet(config.publicKey()) || !isKeySet(config.secretKey())) {
            return false;
        }
        try {
            URI uri = URI.create(config.baseUrl());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isKeySet(String key) {
        return !key.isBlank() && !UNCONFIGURED_KEY.equals(key);
    }

}
