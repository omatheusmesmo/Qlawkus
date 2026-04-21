package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.service.UserMessage;
import dev.omatheusmesmo.qlawkus.cognition.SoulEngine;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(systemMessageProviderSupplier = SoulEngine.class)
@ApplicationScoped
@Logged
public interface AgentService {

    String chat(@UserMessage String message);
}
