package dev.omatheusmesmo.qlawkus.config.metadata;

import java.util.List;

/**
 * One {@code @ConfigMapping} root (for example {@code qlawkus.agent}) and the properties it declares,
 * grouped the same way the generated {@code config-reference.adoc} splits one file per root.
 *
 * @param prefix the config root prefix
 * @param extensionName the owning extension's display name (for example {@code Core}), as reported by
 *                      the bundled metadata
 * @param properties the properties under this root, in declaration order
 */
public record ConfigRootMetadata(String prefix, String extensionName, List<ConfigPropertyMetadata> properties) {
}
