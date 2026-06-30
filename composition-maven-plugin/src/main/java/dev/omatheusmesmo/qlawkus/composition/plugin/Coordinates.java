package dev.omatheusmesmo.qlawkus.composition.plugin;

/**
 * Minimal Maven coordinates (groupId + artifactId) for a capability's extension artifact. Kept free
 * of Maven/Quarkus types so the resolution core stays a pure, unit-testable unit; the mojo maps
 * these onto the devtools coordinate types when it edits the pom.
 */
public record Coordinates(String groupId, String artifactId) {

    public Coordinates {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId is required");
        }
        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("artifactId is required");
        }
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId;
    }
}
