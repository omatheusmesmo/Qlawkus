package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.service.UserMessage;
import dev.omatheusmesmo.qlawkus.cognition.SoulEngine;
import dev.omatheusmesmo.qlawkus.cognition.UpdateSelfStateTool;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(systemMessageProviderSupplier = SoulEngine.class, tools = UpdateSelfStateTool.class)
@ApplicationScoped
@Logged
public interface AgentService {

  Multi<String> chat(@UserMessage String message);
}
