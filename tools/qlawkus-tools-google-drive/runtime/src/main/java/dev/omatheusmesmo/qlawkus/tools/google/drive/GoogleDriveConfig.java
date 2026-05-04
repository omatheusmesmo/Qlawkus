package dev.omatheusmesmo.qlawkus.tools.google.drive;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "qlawkus.google.drive")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface GoogleDriveConfig {

    /**
     * Whether the Google Drive tool is enabled.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Default maximum number of files returned per list request.
     */
    @WithDefault("10")
    int maxResults();

    /**
     * Comma-separated list of Drive API fields to include in file responses.
     * Defaults to id, name, mimeType, modifiedTime, size, webViewLink.
     */
    @WithDefault("id,name,mimeType,modifiedTime,size,webViewLink")
    String fields();
}
