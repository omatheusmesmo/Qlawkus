package dev.omatheusmesmo.qlawkus.console;

import dev.omatheusmesmo.qlawkus.config.metadata.ConfigMetadataIndex;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/** Optional pages follow installed configuration metadata, without importing their extensions. */
@Named("consoleFeatures")
@ApplicationScoped
public class ConsoleFeatures {
    @Inject
    ConfigMetadataIndex metadata;

    public boolean isMcp() {
        return metadata.roots().stream().anyMatch(root -> root.prefix().equals("qlawkus.mcp"));
    }
}
