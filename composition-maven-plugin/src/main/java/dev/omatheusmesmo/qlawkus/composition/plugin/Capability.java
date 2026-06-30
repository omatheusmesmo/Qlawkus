package dev.omatheusmesmo.qlawkus.composition.plugin;

/**
 * A composable capability: the dot-namespaced name used in {@code agent.yml} (e.g. {@code brag},
 * {@code messaging.discord}) paired with the Maven coordinates of the extension that provides it.
 *
 * @param name the manifest capability name
 * @param coordinates the extension artifact that backs it
 */
public record Capability(String name, Coordinates coordinates) {

    public Capability {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("capability name is required");
        }
        if (coordinates == null) {
            throw new IllegalArgumentException("coordinates are required for capability " + name);
        }
    }
}
