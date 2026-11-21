package dev.omatheusmesmo.qlawkus.tool;

import dev.langchain4j.service.tool.ToolProvider;

/**
 * Contributes dynamic tools from an optional extension to the agent's existing tool provider.
 * Implementations own discovery and lifecycle; the core supplies turn context, metrics and spans.
 * Names must be stable and must not collide with another contribution or a local tool.
 * Payload capture is always disabled for contributed executors, since external results can contain secrets.
 */
public interface QlawToolContributor extends ToolProvider {
}
