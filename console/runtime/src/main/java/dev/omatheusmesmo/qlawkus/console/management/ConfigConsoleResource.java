package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.compose.ConfigOverridesAdminService;
import dev.omatheusmesmo.qlawkus.config.RuntimeToggleConfig;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigMetadataIndex;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigPropertyMetadata;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigRootMetadata;
import dev.omatheusmesmo.qlawkus.dto.ConfigOverridesState;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The full configuration editor page: one section per documented config root, rendered from
 * {@link ConfigMetadataIndex} - never a hand-maintained property list. Read-only rendering happens
 * here (including each property's live effective value and source, via the standard MicroProfile
 * {@link Config#getConfigValue}); saving is driven from the page by HTMX against
 * {@code /api/admin/runtime-toggles} ({@code RUN_TIME}) or {@code /api/admin/config-overrides}
 * ({@code BUILD_TIME}/{@code BUILD_AND_RUN_TIME_FIXED}), mirroring the rest of the console.
 */
@Path("/console/config")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class ConfigConsoleResource {

    private final Template configTemplate;
    private final ConfigMetadataIndex metadataIndex;
    private final ConfigOverridesAdminService configOverridesAdminService;
    private final RuntimeToggleConfig runtimeToggleConfig;
    private final Config mpConfig;

    public ConfigConsoleResource(@Location("console/config.html") Template configTemplate,
                                 ConfigMetadataIndex metadataIndex,
                                 ConfigOverridesAdminService configOverridesAdminService,
                                 RuntimeToggleConfig runtimeToggleConfig) {
        this.configTemplate = configTemplate;
        this.metadataIndex = metadataIndex;
        this.configOverridesAdminService = configOverridesAdminService;
        this.runtimeToggleConfig = runtimeToggleConfig;
        this.mpConfig = ConfigProvider.getConfig();
    }

    @GET
    public TemplateInstance page() throws IOException {
        List<ConfigRootView> roots = new ArrayList<>();
        for (ConfigRootMetadata root : metadataIndex.roots()) {
            List<ConfigPropertyView> properties = new ArrayList<>();
            for (ConfigPropertyMetadata property : root.properties()) {
                properties.add(toView(property));
            }
            roots.add(new ConfigRootView(root.prefix(), root.extensionName(), properties));
        }
        ConfigOverridesState overridesState = configOverridesAdminService.currentState();
        return configTemplate
                .data("active", "config")
                .data("roots", roots)
                .data("rebuildPending", overridesState.staged() != null);
    }

    private ConfigPropertyView toView(ConfigPropertyMetadata metadata) {
        ConfigValue value = mpConfig.getConfigValue(metadata.property());
        boolean liveEditable = metadata.phase() == ConfigPhase.RUN_TIME;
        String shadowWarning = liveEditable ? shadowWarning(value) : null;
        return new ConfigPropertyView(
                metadata.property(),
                metadata.description(),
                metadata.phase().name(),
                liveEditable,
                metadata.typeDescription(),
                metadata.defaultValue(),
                value.getValue(),
                value.getSourceName(),
                shadowWarning);
    }

    /**
     * When the current effective value already comes from a source that outranks the runtime-toggle
     * override file (env, {@code .env}, {@code -D}), saving a new value there would not take effect
     * until that higher-ordinal source is removed - surfaced here rather than discovered by trial and
     * error.
     */
    private String shadowWarning(ConfigValue value) {
        if (value.getSourceName() == null || value.getSourceOrdinal() <= runtimeToggleConfig.overrideOrdinal()) {
            return null;
        }
        return "Currently set by %s (ordinal %d); a saved override here will not take effect until that is removed."
                .formatted(value.getSourceName(), value.getSourceOrdinal());
    }
}
