package dev.omatheusmesmo.qlawkus.composition.plugin;

/**
 * Minimal Maven coordinates for a capability's extension artifact. Kept free of Maven/Quarkus types
 * so the resolution core stays a pure, unit-testable unit; the mojo maps these onto the devtools
 * coordinate types when it edits the pom.
 *
 * <p>{@code version} is nullable, and which case applies is the catalog's call rather than the
 * composer's. A published Quarkus extension is BOM-managed, so it is added with no version exactly as
 * {@code quarkus extension add} does. A capability that resolves to a module of this same reactor has
 * no BOM to manage it, so the catalog supplies one - and supplies the {@code ${project.version}}
 * expression rather than a literal, so the pom keeps building across release version bumps.
 */
public record Coordinates(String groupId, String artifactId, String version) {

    public Coordinates {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId is required");
        }
        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("artifactId is required");
        }
        if (version != null && version.isBlank()) {
            throw new IllegalArgumentException("version must be null or non-blank");
        }
    }

    /** BOM-managed coordinates: added to the pom with no version of their own. */
    public Coordinates(String groupId, String artifactId) {
        this(groupId, artifactId, null);
    }

    @Override
    public String toString() {
        return version == null ? groupId + ":" + artifactId : groupId + ":" + artifactId + ":" + version;
    }
}
