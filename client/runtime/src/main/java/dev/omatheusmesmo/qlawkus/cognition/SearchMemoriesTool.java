package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class SearchMemoriesTool {

  @Inject
  FactStore factStore;

  @Inject
  AgentConfig agentConfig;

  @Tool("Search your long-term memory for user preferences, past decisions, or personal context relevant to the conversation. Use this when the topic might relate to something the user told you before.")
  public List<String> searchMemories(
          @P("What to recall from long-term memory about the user, phrased as a search query "
                  + "(a topic, preference, fact, or question), e.g. \"user's GitHub handle\" "
                  + "or \"where the user works\".") String query) {
        return factStore.search(query, agentConfig.memory().maxResults(), agentConfig.memory().minScore());
  }
}
