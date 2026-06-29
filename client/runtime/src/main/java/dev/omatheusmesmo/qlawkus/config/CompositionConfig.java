package dev.omatheusmesmo.qlawkus.config;

import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Where the composition manifest ({@code agent.yml}) lives. Build-time only: the manifest decides
 * which capabilities are composed into the pom, so it is read before and during augmentation, never
 * re-read at runtime. The runtime override is a separate file owned by the runtime toggle tier.
 */
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
@ConfigMapping(prefix = "qlawkus.composition")
public interface CompositionConfig {

    /**
     * Location of the build-time manifest, relative to the application module's resources. Defaults
     * to {@code qlawkus/agent.yml}. The pom generator and the build report both resolve it here.
     */
    @WithDefault(CompositionPaths.DEFAULT_MANIFEST)
    String manifest();
}
