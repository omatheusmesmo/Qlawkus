package dev.omatheusmesmo.qlawkus.approval;

import dev.langchain4j.service.tool.ToolExecutor;
import java.util.Optional;

/**
 * Looks up the executor of a tool without the approval gate in front of it. Only the approval service
 * uses it, and only to run a call the owner has approved.
 */
public interface ApprovedToolExecutors {

    /** The bare executor of the named {@code @QlawTool} tool, or empty when no such tool is registered. */
    Optional<ToolExecutor> unguardedExecutor(String toolName);
}
