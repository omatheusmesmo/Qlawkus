package dev.omatheusmesmo.qlawkus.console.management;

import java.util.List;

/** One config root section on the config editor page, grouping its properties in declaration order. */
public record ConfigRootView(String prefix, String extensionName, List<ConfigPropertyView> properties) {
}
