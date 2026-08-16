package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.omatheusmesmo.qlawkus.cognition.ClearContextTool;
import dev.omatheusmesmo.qlawkus.cognition.RememberFactTool;
import dev.omatheusmesmo.qlawkus.cognition.RespondWithVoiceTool;
import dev.omatheusmesmo.qlawkus.cognition.SearchMemoriesTool;
import dev.omatheusmesmo.qlawkus.cognition.SearchTranscriptsTool;
import dev.omatheusmesmo.qlawkus.cognition.ActiveMemoryAugmentor;
import dev.omatheusmesmo.qlawkus.cognition.ManageSkillTool;
import dev.omatheusmesmo.qlawkus.cognition.SoulEngine;
import dev.omatheusmesmo.qlawkus.cognition.UpdateSelfStateTool;
import dev.omatheusmesmo.qlawkus.cognition.UpdateUserProfileTool;
import dev.omatheusmesmo.qlawkus.cognition.ViewSkillTool;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProviderSupplier;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Never annotate {@code chat()}/{@code chatSync()} with {@code @Retry}, {@code @CircuitBreaker} or
 * {@code @Fallback}, even though quarkus-langchain4j's own fault-tolerance guide shows exactly that
 * pattern. On a method with {@code tools}, those annotations protect the whole ReAct loop, so a
 * transient failure on a later turn re-runs tool calls that already succeeded on an earlier one -
 * silently duplicating any side effect (see quarkiverse/quarkus-langchain4j#2744, reproduced against
 * the upstream source before filing). Retry/circuit-breaker/fallback belongs on the underlying
 * {@code ChatModel}/{@code StreamingChatModel} instead, where a retry only replays the one failed
 * model call - see {@link dev.omatheusmesmo.qlawkus.model.PrimaryChatGuard}.
 */
@RegisterAiService(
    systemMessageProviderSupplier = SoulEngine.class,
    tools = {UpdateSelfStateTool.class, UpdateUserProfileTool.class, SearchMemoriesTool.class,
            SearchTranscriptsTool.class, RememberFactTool.class, RespondWithVoiceTool.class,
            ClearContextTool.class, ViewSkillTool.class, ManageSkillTool.class},
    toolProviderSupplier = QlawToolProviderSupplier.class,
    retrievalAugmentor = ActiveMemoryAugmentor.class,
    maxSequentialToolInvocations = 100
)
@ApplicationScoped
@Logged
public interface AgentService {

    Multi<String> chat(@MemoryId String conversationId, @UserMessage String message);

    String chatSync(@MemoryId String conversationId, @UserMessage String message);
}
