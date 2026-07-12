package dev.omatheusmesmo.qlawkus.console.management;

/**
 * A single property row rendered by the config editor page: metadata plus the value/source actually
 * in effect right now, and an optional warning when a higher-ordinal source than this property's
 * write destination already shadows it (saving here would silently do nothing until that source
 * changes).
 */
public record ConfigPropertyView(
        String property,
        String description,
        String phase,
        boolean liveEditable,
        String typeDescription,
        String defaultValue,
        String effectiveValue,
        String effectiveSource,
        String shadowWarning
) {
}
