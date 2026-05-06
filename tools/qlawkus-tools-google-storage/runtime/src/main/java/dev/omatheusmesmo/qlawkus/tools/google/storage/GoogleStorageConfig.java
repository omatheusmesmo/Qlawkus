package dev.omatheusmesmo.qlawkus.tools.google.storage;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "qlawkus.google.storage")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface GoogleStorageConfig {

    /**
     * Whether the Google Cloud Storage tool is enabled.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Default Google Cloud project ID used for listing buckets.
     * Required when using listBuckets without explicit projectId parameter.
     */
    Optional<String> projectId();
}
