package dev.omatheusmesmo.qlawkus.tool.access;

/**
 * The class of one tool and how the build reached it.
 *
 * @param tool the name the model sees
 * @param accessClass what the tool does
 * @param source whether the class was declared, inferred or fell back to the most restrictive
 * @param origin the bean and method that declare the tool, for the build report
 */
public record ToolAccessEntry(String tool, AccessClass accessClass, AccessSource source, String origin) {
}
