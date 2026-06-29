package dev.omatheusmesmo.qlawkus.composition;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The {@code build-time} section of {@code agent.yml}: which capabilities the pom generator should
 * include. Expressed as policy + exceptions - a single {@link Posture} default plus the list of
 * capabilities that get the opposite effect.
 *
 * @param defaultPosture the posture applied to every capability not listed in {@code except}
 * @param except the capabilities whose effect is the opposite of {@code defaultPosture}
 */
public record BuildTime(
        @JsonProperty("default") Posture defaultPosture,
        List<String> except) {

    public BuildTime {
        if (defaultPosture == null) {
            throw new IllegalArgumentException("build-time.default is required ('enabled' or 'disabled')");
        }
        except = except == null ? List.of() : List.copyOf(except);
    }

    /**
     * Whether the manifest asks for {@code capability} to be composed in. This answers intent only;
     * a capability whose module is absent from the classpath is never wired regardless of the
     * answer here (you cannot enable what is not a dependency).
     *
     * @param capability the dot-namespaced capability name (e.g. {@code messaging.discord})
     * @return true when the policy resolves to enabled for this capability
     */
    public boolean isEnabled(String capability) {
        boolean listed = except.contains(capability);
        return defaultPosture == Posture.ENABLED ? !listed : listed;
    }
}
