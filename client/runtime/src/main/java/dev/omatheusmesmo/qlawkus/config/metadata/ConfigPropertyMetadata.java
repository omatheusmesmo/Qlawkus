package dev.omatheusmesmo.qlawkus.config.metadata;

import io.quarkus.runtime.annotations.ConfigPhase;

import java.util.List;

/**
 * One documented configuration property, as read from the {@code quarkus-config-doc} metadata bundled
 * in every extension's runtime jar - the same source the {@code quarkus-config-doc-maven-plugin} reads
 * to generate {@code config-reference.adoc}. Lets the config editor render a form and validate a
 * submitted value without a hand-maintained property list.
 *
 * @param property the dotted configuration property name (for example {@code qlawkus.agent.timezone})
 * @param rootPrefix the {@code @ConfigMapping} prefix this property belongs to (for example
 *                    {@code qlawkus.agent}), used to group properties in the UI
 * @param environmentVariable the derived environment-variable name, or {@code null}
 * @param phase whether the property applies live ({@link ConfigPhase#RUN_TIME}) or needs a rebuild
 *              ({@link ConfigPhase#BUILD_TIME} / {@link ConfigPhase#BUILD_AND_RUN_TIME_FIXED})
 * @param type the fully-qualified value type (for example {@code java.time.Duration})
 * @param typeDescription a human-readable type label (for example {@code duration})
 * @param defaultValue the default value as documented, or {@code null} when none
 * @param optional whether the property may be left unset
 * @param allowedValues the accepted values for an enum-typed property, empty otherwise
 * @param description the Javadoc-derived description, or {@code null} when none
 */
public record ConfigPropertyMetadata(
        String property,
        String rootPrefix,
        String environmentVariable,
        ConfigPhase phase,
        String type,
        String typeDescription,
        String defaultValue,
        boolean optional,
        List<String> allowedValues,
        String description
) {
}
