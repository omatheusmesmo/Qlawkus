package dev.omatheusmesmo.qlawkus.langfuse.deployment;

import java.util.Optional;

import dev.omatheusmesmo.qlawkus.langfuse.LangfuseExportRecorder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.quarkiverse.langfuse.deployment.config.LangfuseBuildTimeConfig;
import io.quarkus.arc.deployment.OpenTelemetrySdkBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;
import jakarta.inject.Singleton;
import org.jboss.jandex.ClassType;

class LangfuseExportProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("qlawkus-observability-langfuse");
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerExporter(LangfuseBuildTimeConfig upstream, Optional<OpenTelemetrySdkBuildItem> sdk,
            CoreVertxBuildItem vertx, LangfuseExportRecorder recorder,
            BuildProducer<ReflectiveClassBuildItem> reflection, BuildProducer<SyntheticBeanBuildItem> beans) {
        if (upstream.otel().enabled()) {
            throw new ConfigurationException("Set quarkus.langfuse.otel.enabled=false when using observability.langfuse."
                    + " Qlawkus registers its own filtered Langfuse processor.");
        }
        if (sdk.map(OpenTelemetrySdkBuildItem::isTracingBuildTimeEnabled).orElse(false)) {
            String generation = QuarkusClassLoader
                    .isClassPresentAtRuntime("io.opentelemetry.exporter.otlp.internal.HttpExporter") ? "4" : "3";
            String exporterFactory = "io.quarkiverse.langfuse.runtime.otel.LangfuseSpanExporterFactoryQuarkus" + generation;
            reflection.produce(ReflectiveClassBuildItem.builder(exporterFactory).constructors().build());
            beans.produce(SyntheticBeanBuildItem.configure(SpanProcessor.class)
                    .scope(Singleton.class).setRuntimeInit().unremovable()
                    .createWith(recorder.create(exporterFactory, OpenTelemetrySdkBuildItem.isOtelSdkEnabled(sdk),
                            vertx.getVertx()))
                    .addInjectionPoint(ClassType.create(TlsConfigurationRegistry.class)).done());
        }
    }
}
