package dev.omatheusmesmo.qlawkus.composition;

/**
 * Filesystem and classpath conventions for the composition manifest. Constants only, so the
 * generator and the runtime agree on where the files live.
 */
public final class CompositionPaths {

    /**
     * Default location of the build-time manifest, relative to the application module. Read by the
     * pom generator and bundled as a classpath resource so the running app can locate it.
     */
    public static final String DEFAULT_MANIFEST = "qlawkus/agent.yml";

    /**
     * Default location of the runtime override, relative to the user home. Live runtime-tier
     * changes persist here, never back into the baked artifact (behavior owned by the runtime tier).
     */
    public static final String DEFAULT_RUNTIME_OVERRIDE = ".qlawkus/agent.runtime.yml";

    private CompositionPaths() {
    }
}
