package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class SearchMemoriesTool {

  @Inject
  FactStore factStore;

  @Tool("Search your long-term memory for user preferences, past decisions, or personal context relevant to the conversation. Use this when the topic might relate to something the user told you before.")
  public List<String> searchMemories(String query) {
    return factStore.search(query, 5, 0.75);
  }
}
