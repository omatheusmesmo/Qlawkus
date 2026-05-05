package dev.omatheusmesmo.qlawkus.tools.google.sheets;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "qlawkus.google.sheets")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface GoogleSheetsConfig {

    /**
     * Whether the Google Sheets tool is enabled.
     */
    @WithDefault("false")
    boolean enabled();

    /**
     * Default value input option for write operations.
     * Valid values: RAW, USER_ENTERED. USER_ENTERED parses dates/formulas like the Sheets UI.
     */
    @WithDefault("USER_ENTERED")
    String valueInputOption();
}
