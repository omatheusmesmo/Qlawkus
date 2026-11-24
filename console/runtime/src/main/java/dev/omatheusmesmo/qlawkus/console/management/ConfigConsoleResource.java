package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.compose.ConfigOverridesAdminService;
import dev.omatheusmesmo.qlawkus.config.RuntimeToggleConfig;
import dev.omatheusmesmo.qlawkus.config.RuntimeToggleWriter;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigMetadataIndex;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigPropertyMetadata;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigRootMetadata;
import dev.omatheusmesmo.qlawkus.dto.ConfigOverridesState;
import io.quarkus.qute.Location;
import io.quarkus.qute.RawString;
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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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

    static final String PENDING_RESTART = "override file, after restart";
    static final String PENDING_REBUILD = "staged, after rebuild";

    private final Template configTemplate;
    private final ConfigMetadataIndex metadataIndex;
    private final ConfigOverridesAdminService configOverridesAdminService;
    private final RuntimeToggleConfig runtimeToggleConfig;
    private final RuntimeToggleWriter runtimeToggleWriter;
    private final Config mpConfig;

    public ConfigConsoleResource(@Location("console/config.html") Template configTemplate,
                                 ConfigMetadataIndex metadataIndex,
                                 ConfigOverridesAdminService configOverridesAdminService,
                                 RuntimeToggleConfig runtimeToggleConfig,
                                 RuntimeToggleWriter runtimeToggleWriter) {
        this.configTemplate = configTemplate;
        this.metadataIndex = metadataIndex;
        this.configOverridesAdminService = configOverridesAdminService;
        this.runtimeToggleConfig = runtimeToggleConfig;
        this.runtimeToggleWriter = runtimeToggleWriter;
        this.mpConfig = ConfigProvider.getConfig();
    }

    @GET
    public TemplateInstance page() throws IOException {
        ConfigOverridesState overridesState = configOverridesAdminService.currentState();
        Set<String> pendingRuntime = runtimeToggleWriter.all().keySet();
        Set<String> pendingBuild = stagedProperties(overridesState.staged());
        List<ConfigRootView> roots = new ArrayList<>();
        Set<String> anchors = new HashSet<>();
        for (ConfigRootMetadata root : metadataIndex.roots()) {
            List<ConfigPropertyView> properties = new ArrayList<>();
            for (ConfigPropertyMetadata property : root.properties()) {
                properties.add(toView(property, pendingRuntime, pendingBuild));
            }
            roots.add(ConfigRootView.of(anchors, root.prefix(), root.extensionName(), properties));
        }
        return configTemplate
                .data("active", "config")
                .data("roots", roots)
                .data("rebuildPending", overridesState.staged() != null);
    }

    private ConfigPropertyView toView(ConfigPropertyMetadata metadata, Set<String> pendingRuntime,
                                      Set<String> pendingBuild) {
        ConfigValue value = mpConfig.getConfigValue(metadata.property());
        boolean liveEditable = metadata.phase() == ConfigPhase.RUN_TIME;
        String shadowWarning = liveEditable ? shadowWarning(value) : null;
        String pending = pendingLabel(metadata.property(), liveEditable, pendingRuntime, pendingBuild);
        return new ConfigPropertyView(
                metadata.property(),
                descriptionOf(metadata),
                metadata.phase().name(),
                liveEditable,
                metadata.typeDescription(),
                metadata.defaultValue(),
                value.getValue(),
                value.getSourceName(),
                ConfigSourceLabel.of(value.getSourceName()),
                shadowWarning,
                !ConfigSourceLabel.isDefault(value.getSourceName()) || pending != null,
                ConfigSourceLabel.isOwnerSource(value.getSourceName()) || pending != null,
                pending);
    }

    private static String pendingLabel(String property, boolean liveEditable, Set<String> pendingRuntime,
                                       Set<String> pendingBuild) {
        if (liveEditable) {
            return pendingRuntime.contains(property) ? PENDING_RESTART : null;
        }
        return pendingBuild.contains(property) ? PENDING_REBUILD : null;
    }

    private static Set<String> stagedProperties(String staged) throws IOException {
        if (staged == null || staged.isBlank()) {
            return Set.of();
        }
        Properties properties = new Properties();
        properties.load(new StringReader(staged));
        return properties.stringPropertyNames();
    }

    private static RawString descriptionOf(ConfigPropertyMetadata metadata) {
        String html = JavadocText.toHtml(metadata.description());
        return html.isEmpty() ? null : new RawString(html);
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
                .formatted(ConfigSourceLabel.of(value.getSourceName()), value.getSourceOrdinal());
    }
}
