package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.omatheusmesmo.qlawkus.cognition.ClearContextTool;
import dev.omatheusmesmo.qlawkus.cognition.RememberFactTool;
import dev.omatheusmesmo.qlawkus.cognition.RespondWithVoiceTool;
import dev.omatheusmesmo.qlawkus.cognition.SearchMemoriesTool;
import dev.omatheusmesmo.qlawkus.cognition.ActiveMemoryAugmentor;
import dev.omatheusmesmo.qlawkus.cognition.SoulEngine;
import dev.omatheusmesmo.qlawkus.cognition.UpdateSelfStateTool;
import dev.omatheusmesmo.qlawkus.cognition.UpdateUserProfileTool;
import dev.omatheusmesmo.qlawkus.tool.ClawToolProviderSupplier;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(
    systemMessageProviderSupplier = SoulEngine.class,
    tools = {UpdateSelfStateTool.class, UpdateUserProfileTool.class, SearchMemoriesTool.class,
            RememberFactTool.class, RespondWithVoiceTool.class, ClearContextTool.class},
    toolProviderSupplier = ClawToolProviderSupplier.class,
    retrievalAugmentor = ActiveMemoryAugmentor.class,
    maxSequentialToolInvocations = 100
)
@ApplicationScoped
@Logged
public interface AgentService {

    Multi<String> chat(@MemoryId String conversationId, @UserMessage String message);

    String chatSync(@MemoryId String conversationId, @UserMessage String message);
}
