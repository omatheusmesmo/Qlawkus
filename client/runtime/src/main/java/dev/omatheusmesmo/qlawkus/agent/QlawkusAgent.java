package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ToolProviderSupplier;
import dev.langchain4j.agentic.declarative.ToolsSupplier;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolProvider;
import dev.omatheusmesmo.qlawkus.cognition.ClearContextTool;
import dev.omatheusmesmo.qlawkus.cognition.RememberFactTool;
import dev.omatheusmesmo.qlawkus.cognition.RespondWithVoiceTool;
import dev.omatheusmesmo.qlawkus.cognition.SearchMemoriesTool;
import dev.omatheusmesmo.qlawkus.cognition.SearchTranscriptsTool;
import dev.omatheusmesmo.qlawkus.cognition.UpdateSelfStateTool;
import dev.omatheusmesmo.qlawkus.cognition.UpdateUserProfileTool;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import io.quarkus.arc.Arc;

/**
 * The single OpenClaw-style agent, declared the Quarkus-idiomatic (declarative) way so the framework
 * wires memoryId, tools and model correctly at build time.
 *
 * <ul>
 *   <li>{@code @MemoryId} -> Quarkus ChatMemoryProvider backed by the selected WorkingMemoryStore (persistent, keyed by conversation).</li>
 *   <li>No {@code @ChatModelSupplier}/{@code @ModelName} -> uses the default ChatModel bean = FallbackChatModel (resilience).</li>
 *   <li>{@code @SystemMessage("{{soul}}")} -> dynamic soul injected per call by callers (SoulEngine), since declarative agents have no system-message-provider hook.</li>
 *   <li>{@code @ToolProviderSupplier} -> QlawToolProvider (Google + dynamically discovered tools); the agent has no per-tool/module coupling.</li>
 *   <li>{@code @ToolsSupplier} -> the in-module cognition tools.</li>
 * </ul>
 */
public interface QlawkusAgent {

    @Agent
    @SystemMessage("{{soul}}")
    @UserMessage("{{message}}")
    String chat(@MemoryId String memoryId, @V("soul") String soul, @V("message") String message);

    @ToolProviderSupplier
    static ToolProvider toolProvider() {
        return Arc.container().instance(QlawToolProvider.class).get();
    }

    @ToolsSupplier
    static Object[] cognitionTools() {
        return new Object[] {
            Arc.container().instance(UpdateSelfStateTool.class).get(),
            Arc.container().instance(UpdateUserProfileTool.class).get(),
            Arc.container().instance(SearchMemoriesTool.class).get(),
            Arc.container().instance(SearchTranscriptsTool.class).get(),
            Arc.container().instance(RememberFactTool.class).get(),
            Arc.container().instance(RespondWithVoiceTool.class).get(),
            Arc.container().instance(ClearContextTool.class).get()
        };
    }
}
