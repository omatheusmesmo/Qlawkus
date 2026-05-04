package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.file")
public interface FileConfig {

    /**
     * Maximum file size in bytes allowed for read operations.
     */
    @WithDefault("5242880")
    long maxReadSize();

    /**
     * Maximum file size in bytes allowed for write operations.
     */
    @WithDefault("10485760")
    long maxWriteSize();

    /**
     * Character encoding for file read/write operations.
     */
    @WithDefault("UTF-8")
    String encoding();
}
