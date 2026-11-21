package dev.omatheusmesmo.qlawkus.tools.mcp;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Masks inherited values because upstream STDIO overlays ProcessBuilder's inherited environment.
 * Excluded names remain present with empty values; no external launcher is required.
 */
final class McpEnvironment {
  private static final Set<String> ALLOWED = Set.of("PATH", "HOME", "USER", "LANG", "TZ", "TMPDIR");

  static Map<String, String> masked(Map<String, String> inherited, Map<String, String> referenced) {
    Map<String, String> result = new HashMap<>();
    inherited.forEach((name, value) -> result.put(name,
        ALLOWED.contains(name) || name.matches("LC_[A-Z_]+") ? value : ""));
    result.putAll(referenced);
    return Map.copyOf(result);
  }
}
