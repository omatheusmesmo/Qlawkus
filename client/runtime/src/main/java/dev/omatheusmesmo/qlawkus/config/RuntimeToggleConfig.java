package dev.omatheusmesmo.qlawkus.config;

import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Runtime-toggle tier of the composition manifest: capabilities already compiled in, re-read each
 * boot and overridable live without a rebuild. The baked-in {@code runtime:} block of
 * {@code agent.yml} supplies defaults; an external override file (a mounted volume, never the
 * artifact) overrides them; environment variables and system properties override both. The layering
 * is by {@code ConfigSource} ordinal, the mirror of the secrets tier - same discipline, opposite
 * intent: secrets outrank env, toggles yield to it, so a deploy-time value always wins.
 *
 * <p>These knobs are read by {@code RuntimeToggleConfigSourceFactory} while the config is bootstrapped;
 * the mapping exists so they appear, documented, in the generated configuration reference.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.runtime")
public interface RuntimeToggleConfig {

    /**
     * Whether the runtime-toggle config sources are published. When false neither the baked-in
     * defaults nor the external override contribute, and only ordinary configuration applies.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Classpath location of the baked-in manifest whose {@code runtime:} block supplies the toggle
     * defaults. Defaults to {@code qlawkus/agent.yml} - the same manifest the pom generator reads.
     */
    @WithDefault(CompositionPaths.DEFAULT_MANIFEST)
    String manifest();

    /**
     * Filesystem path of the external override, a standalone YAML map of toggles kept outside the
     * artifact (mount it on a volume). A leading {@code ~} expands to the user home; an absent file
     * contributes nothing. Defaults to {@code ~/.qlawkus/agent.runtime.yml}.
     */
    @WithDefault("~/" + CompositionPaths.DEFAULT_RUNTIME_OVERRIDE)
    String overridePath();

    /**
     * Ordinal at which the baked-in defaults publish. Kept low (default 250, the
     * {@code application.properties} level) so the external override, environment variables, and
     * {@code -D} system properties all win over a baked default.
     */
    @WithDefault("250")
    int bakedOrdinal();

    /**
     * Ordinal at which the external override publishes. Default 290: above the baked-in defaults but
     * below environment variables (300) and system properties (400), so an operator's env or
     * {@code -D} still overrides a persisted toggle.
     */
    @WithDefault("290")
    int overrideOrdinal();
}
