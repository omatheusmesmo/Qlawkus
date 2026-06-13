package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class RememberFactTool {

    @Inject
    FactStore factStore;

    @Tool("""
            Save a fact about the user to long-term memory so it persists across conversations. \
            Use this whenever the user explicitly asks you to remember something ("lembre-se que...", \
            "remember X", "registra isso", "anota aí") OR when you learn an important piece of context \
            about them (their name, GitHub handle, role, preferences, projects, contact info, etc) \
            that would be useful in future conversations. \
            \
            Phrase the fact as a complete statement in third person, e.g. "User's GitHub handle is \
            omatheusmesmo" instead of just "omatheusmesmo". This makes the fact retrievable later via \
            semantic search. \
            \
            Only store durable facts that will still matter later. Do NOT store ephemeral details — \
            task progress, PR/issue numbers, commit hashes, "fixed bug X", or anything that goes stale \
            within a week. Write declarative facts ("User prefers dark mode"), not instructions to \
            yourself ("Always use dark mode").""")
    public String rememberFact(@P("The fact to remember, as a complete third-person statement") String fact) {
        if (fact == null || fact.isBlank()) {
            return "Cannot store an empty fact.";
        }
        try {
            factStore.store(fact, Map.of(
                    "source", MemorySource.REMEMBER_TOOL.value(),
                    "stored_at", Instant.now().toString()));
            Log.infof("RememberFactTool: stored fact='%s'", fact);
            return "Stored in long-term memory: " + fact;
        } catch (Exception e) {
            Log.errorf(e, "RememberFactTool: failed to store fact");
            return "Failed to store the fact: " + e.getMessage();
        }
    }
}
