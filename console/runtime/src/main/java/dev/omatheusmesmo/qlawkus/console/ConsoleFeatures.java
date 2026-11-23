package dev.omatheusmesmo.qlawkus.console;

import dev.omatheusmesmo.qlawkus.artifact.ArtifactLibrary;
import dev.omatheusmesmo.qlawkus.config.metadata.ConfigMetadataIndex;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/** Optional pages follow installed capabilities without importing their extensions. */
@Named("consoleFeatures")
@ApplicationScoped
public class ConsoleFeatures {
    @Inject
    ConfigMetadataIndex metadata;

    @Inject
    Instance<ArtifactLibrary> artifacts;

    public boolean isArtifacts() {
        return artifacts.isResolvable();
    }

    public boolean isMcp() {
        return metadata.roots().stream().anyMatch(root -> root.prefix().equals("qlawkus.mcp"));
    }

    public boolean isSpecialists() {
        return metadata.roots().stream().anyMatch(root -> root.prefix().equals("qlawkus.specialists"));
    }
}
