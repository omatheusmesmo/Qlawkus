package dev.omatheusmesmo.qlawkus.composition;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * The whole {@code agent.yml} composition manifest. The single source of truth shared by the pom
 * generator (which reads {@link #buildTime()} before the build) and the running app (which reads
 * {@link #runtime()} each boot), so the schema and policy logic never diverge between them.
 *
 * @param version schema version; only {@link #SUPPORTED_VERSION} is currently accepted
 * @param buildTime which capabilities to compose into the pom
 * @param runtime free-form, dot-namespaced runtime toggles (e.g. {@code skill-hub.approval-mode})
 */
public record CompositionManifest(
        int version,
        @JsonProperty("build-time") BuildTime buildTime,
        Map<String, Object> runtime) {

    public static final int SUPPORTED_VERSION = 1;

    public CompositionManifest {
        if (version != SUPPORTED_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported manifest version: " + version + " (supported: " + SUPPORTED_VERSION
                            + "). Add 'version: " + SUPPORTED_VERSION + "' at the top of agent.yml.");
        }
        if (buildTime == null) {
            throw new IllegalArgumentException("agent.yml is missing the 'build-time' section");
        }
        runtime = runtime == null ? Map.of() : Map.copyOf(runtime);
    }
}
