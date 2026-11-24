package dev.omatheusmesmo.qlawkus.langfuse;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.observability.langfuse")
public interface LangfuseExportConfig {

    /** Whether to export agent trace metadata when Langfuse and OpenTelemetry are configured. */
    @WithDefault("true")
    boolean enabled();
}
