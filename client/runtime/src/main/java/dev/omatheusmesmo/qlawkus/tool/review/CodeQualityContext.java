package dev.omatheusmesmo.qlawkus.tool.review;

import java.util.List;

/**
 * Bundles the diff under review with Clean Code principles retrieved from
 * Semantic Memory. Returned to the agent so it can reason over both pieces
 * together and produce actionable feedback.
 *
 * @param diff     the raw unified diff text to evaluate
 * @param rules    Clean Code / best-practice rules recalled from memory (may be empty
 *                 if the agent has not yet learned any)
 * @param note     short instruction for the agent on how to use this context
 */
public record CodeQualityContext(
        String diff,
        List<String> rules,
        String note
) {
}
