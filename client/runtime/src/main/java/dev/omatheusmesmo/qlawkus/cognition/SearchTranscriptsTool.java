package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.MemorySource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class SearchTranscriptsTool {

  @Inject
  FactStore factStore;

  @ConfigProperty(name = "qlawkus.agent.transcript-search.max-results", defaultValue = "5")
  int maxResults;

  @ConfigProperty(name = "qlawkus.agent.transcript-search.min-score", defaultValue = "0.7")
  double minScore;

  @Tool("""
      Search what was actually said in past conversations (raw transcripts), to recall a specific \
      exchange, decision, or detail the owner mentioned before. Use this when the owner references \
      something from an earlier conversation and it isn't already in your context — this is finer \
      grained than your long-term facts.""")
  public List<String> searchTranscripts(
      @P("What to find from past conversations, phrased as a search query") String query) {
    return factStore.searchBySource(query, MemorySource.TRANSCRIPT.value(), maxResults, minScore);
  }
}
