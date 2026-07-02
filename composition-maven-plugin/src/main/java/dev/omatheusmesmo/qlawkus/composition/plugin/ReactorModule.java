package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.nio.file.Path;

/**
 * A reactor module the catalog inspects for a composition capability: its Maven coordinates plus the
 * path to its {@code quarkus-extension.yaml} source template. Kept free of Maven types so
 * {@link ReactorCatalog} stays unit-testable with plain temp files; {@code GenerateMojo} maps each
 * reactor {@code MavenProject} onto one of these.
 *
 * @param groupId the module groupId
 * @param artifactId the module artifactId
 * @param descriptor path to {@code src/main/resources/META-INF/quarkus-extension.yaml} (may not exist)
 */
public record ReactorModule(String groupId, String artifactId, Path descriptor) {

    public ReactorModule {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId is required");
        }
        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("artifactId is required");
        }
    }
}
