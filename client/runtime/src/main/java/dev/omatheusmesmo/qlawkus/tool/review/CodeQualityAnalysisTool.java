package dev.omatheusmesmo.qlawkus.tool.review;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Enriches a code diff with Clean Code principles retrieved from Semantic Memory
 * so the agent can evaluate code quality without hallucinating rules.
 *
 * <p>The tool itself does not judge the code — it provides context. The agent
 * reasons over {@link CodeQualityContext#diff()} and {@link CodeQualityContext#rules()}
 * to produce review comments.
 *
 * <p>If no relevant rules are found in memory the context still carries the diff,
 * so the agent can fall back to general reasoning. Rules accumulate over time as
 * the user discusses Clean Code practices in conversation (via SemanticExtractorObserver).
 */
@ClawTool
@ApplicationScoped
public class CodeQualityAnalysisTool {

    static final String RULES_QUERY = "Clean Code principles best practices code quality";
    static final int MAX_RULES = 8;
    static final double MIN_SCORE = 0.75;

    static final String AGENT_NOTE =
            "Apply each rule to the diff above. For every violation found, cite the rule, "
                    + "the file and line number, and suggest a concrete fix. "
                    + "If no rules are available, apply general Clean Code reasoning.";

    @Inject
    FactStore factStore;

    @Tool("""
            Retrieve Clean Code principles from Semantic Memory and bundle them with
            the provided diff so you can perform a structured code-quality review.
            Returns the diff, matched rules, and a note on how to apply them.
            Parameter: gitDiff (required) — unified diff text, e.g. output of 'git diff HEAD~1'.
            """)
    public CodeQualityContext analyzeCodeQuality(
            @P("Unified diff text to review, e.g. output of 'git diff HEAD~1' or 'gh pr diff <number>'")
            String gitDiff) {

        if (gitDiff == null || gitDiff.isBlank()) {
            Log.warnf("CodeQualityAnalysisTool: received blank diff");
            return new CodeQualityContext("", List.of(), "No diff provided. Obtain the diff first with 'git diff' or 'gh pr diff'.");
        }

        List<String> rules;
        try {
            rules = factStore.search(RULES_QUERY, MAX_RULES, MIN_SCORE);
        } catch (Exception e) {
            Log.warnf(e, "CodeQualityAnalysisTool: semantic memory search failed, proceeding without rules");
            rules = List.of();
        }

        Log.infof("CODE_QUALITY | diff_chars=%d rules_found=%d", gitDiff.length(), rules.size());
        return new CodeQualityContext(gitDiff, rules, AGENT_NOTE);
    }
}
