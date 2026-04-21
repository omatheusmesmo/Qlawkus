package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.service.UserMessage;
import dev.omatheusmesmo.qlawkus.cognition.SoulEngine;
import dev.omatheusmesmo.qlawkus.cognition.UpdateSelfStateTool;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(systemMessageProviderSupplier = SoulEngine.class, tools = UpdateSelfStateTool.class)
@ApplicationScoped
@Logged
public interface AgentService {

    String chat(@UserMessage String message);
}
