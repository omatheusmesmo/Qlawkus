package dev.omatheusmesmo.qlawkus.console.management;

import io.quarkus.qute.RawString;

/**
 * A single property row rendered by the config editor page: metadata plus the value/source actually
 * in effect right now, and an optional warning when a higher-ordinal source than this property's
 * write destination already shadows it (saving here would silently do nothing until that source
 * changes). {@code changed} is true when the effective value comes from anything but the default, or an override
 * is waiting for a restart or rebuild. {@code ownerSet} narrows that to what the owner did: a source they
 * control or a pending override, never the distribution's own property files. {@code pending} names what
 * a waiting override needs, or is {@code null}. The description is pre-rendered, escaped HTML
 * ({@link JavadocText}) and the source carries both its short label and its full name.
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
        String shadowWarning,
        boolean changed,
        boolean ownerSet,
        String pending
) {

    /** The value the editor's phase filter reads: {@code run} applies on restart, {@code build} on rebuild. */
    public String phaseKey() {
        return liveEditable ? "run" : "build";
    }
}
