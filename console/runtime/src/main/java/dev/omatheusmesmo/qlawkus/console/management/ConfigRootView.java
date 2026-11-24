package dev.omatheusmesmo.qlawkus.console.management;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One config root section on the config editor page, grouping its properties in declaration order.
 * The anchor is the section's element id, derived from the prefix and unique on the page (two roots
 * can share a prefix), so a link or the section index can address exactly one section.
 */
public record ConfigRootView(String anchor, String prefix, String extensionName, List<ConfigPropertyView> properties) {

    /** Builds a view whose anchor is not in {@code taken}, adding the new anchor to it. */
    static ConfigRootView of(Set<String> taken, String prefix, String extensionName,
                             List<ConfigPropertyView> properties) {
        String base = "cfg-" + prefix.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        String anchor = base;
        for (int suffix = 2; !taken.add(anchor); suffix++) {
            anchor = base + "-" + suffix;
        }
        return new ConfigRootView(anchor, prefix, extensionName, properties);
    }

    public long changedCount() {
        return properties.stream().filter(ConfigPropertyView::changed).count();
    }

    public boolean hasChanged() {
        return properties.stream().anyMatch(ConfigPropertyView::changed);
    }

    public boolean hasOwnerSet() {
        return properties.stream().anyMatch(ConfigPropertyView::ownerSet);
    }
}
