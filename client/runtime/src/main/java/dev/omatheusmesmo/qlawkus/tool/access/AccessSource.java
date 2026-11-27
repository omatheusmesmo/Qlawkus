package dev.omatheusmesmo.qlawkus.tool.access;

/** How the build arrived at the {@link AccessClass} of a tool. */
public enum AccessSource {
    /** Written on the tool with {@link ToolAccess}. */
    DECLARED,
    /** Read from the leading verb of the method name. */
    INFERRED,
    /** No annotation and no recognised verb, so the most restrictive class applies. */
    FALLBACK
}
