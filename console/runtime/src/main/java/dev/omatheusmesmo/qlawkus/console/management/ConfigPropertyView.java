package dev.omatheusmesmo.qlawkus.console.management;

import io.quarkus.qute.RawString;

/**
 * A single property row rendered by the config editor page: metadata plus the value/source actually
 * in effect right now, and an optional warning when a higher-ordinal source than this property's
 * write destination already shadows it (saving here would silently do nothing until that source
 * changes). The description is pre-rendered, escaped HTML ({@link JavadocText}) and the source carries
 * both its short label and its full name.
 */
public record ConfigPropertyView(
        String property,
        RawString description,
        String phase,
        boolean liveEditable,
        String typeDescription,
        String defaultValue,
        String effectiveValue,
        String effectiveSource,
        String sourceLabel,
        String shadowWarning
) {
}
