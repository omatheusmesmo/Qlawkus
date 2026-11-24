package dev.omatheusmesmo.qlawkus.decision;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.decision.nvidia")
public interface NvidiaDecisionConfig {

    /** Whether to send completed-turn text to NVIDIA to decide which extractors to run. */
    @WithDefault("true")
    boolean enabled();

    /** NVIDIA API key. Defaults to the primary model's key when it already uses NVIDIA's hosted endpoint. */
    Optional<String> apiKey();

    /** NVIDIA hosted model that returns Yes and No token log probabilities. */
    @WithDefault("nvidia/nemotron-3.5-lightning-30b-a3b")
    String model();

    /** Minimum normalized yes score for the durable-fact extractor. */
    @WithDefault("0.5")
    @DecimalMin("0")
    @DecimalMax("1")
    double durableFactThreshold();

    /** Minimum normalized yes score for the reusable-procedure extractor. */
    @WithDefault("0.5")
    @DecimalMin("0")
    @DecimalMax("1")
    double reusableProcedureThreshold();

    /** Minimum normalized yes score for the achievement extractor. */
    @WithDefault("0.5")
    @DecimalMin("0")
    @DecimalMax("1")
    double achievementThreshold();
}
