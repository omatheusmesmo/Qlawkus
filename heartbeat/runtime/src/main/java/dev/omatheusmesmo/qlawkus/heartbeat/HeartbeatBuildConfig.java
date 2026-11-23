package dev.omatheusmesmo.qlawkus.heartbeat;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "qlawkus.heartbeat")
public interface HeartbeatBuildConfig {

    /** Use the named LangChain4j heartbeat DecisionModel in place of the local chat classifier. */
    @WithDefault("false")
    boolean typedDecisionModel();
}
