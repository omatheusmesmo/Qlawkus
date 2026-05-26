package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.internal.PendingResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.output.OutputParsingException;
import dev.langchain4j.service.tool.ToolProvider;
import dev.omatheusmesmo.qlawkus.cognition.ClearContextTool;
import dev.omatheusmesmo.qlawkus.cognition.GoogleAuthRequiredIndicator;
import dev.omatheusmesmo.qlawkus.cognition.RememberFactTool;
import dev.omatheusmesmo.qlawkus.cognition.RespondWithVoiceTool;
import dev.omatheusmesmo.qlawkus.cognition.SearchMemoriesTool;
import dev.omatheusmesmo.qlawkus.cognition.SoulEngine;
import dev.omatheusmesmo.qlawkus.cognition.UpdateSelfStateTool;
import dev.omatheusmesmo.qlawkus.tool.ClawToolProvider;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class QlawkusAgentWorkflow {

    private static final String OAUTH_RESPONSE_ID = "google-oauth-approval";
    private static final String DELIVERY_PROVIDER_KEY = "deliveryProviderId";
    private static final String DELIVERY_CHAT_KEY = "deliveryChatId";

    @Inject
    ChatModel chatModel;

    @Inject
    ClawToolProvider clawToolProvider;

    @Inject
    SoulEngine soulEngine;

    @Inject
    GoogleAuthRequiredIndicator authRequiredIndicator;

    UntypedAgent workflow;

    @PostConstruct
    void init() {
        UntypedAgent chatAgent = AgenticServices.agentBuilder()
            .chatModel(chatModel)
            .systemMessageProvider(memoryId -> soulEngine.getSystemMessage(memoryId).orElse(null))
            .tools(Arc.container().instance(UpdateSelfStateTool.class).get(),
                Arc.container().instance(SearchMemoriesTool.class).get(),
                Arc.container().instance(RememberFactTool.class).get(),
                Arc.container().instance(RespondWithVoiceTool.class).get(),
                Arc.container().instance(ClearContextTool.class).get())
            .toolProvider(clawToolProvider)
            .outputKey("chatResponse")
            .build();

        HumanInTheLoop oauthGate = AgenticServices.humanInTheLoopBuilder()
            .description("Wait for Google OAuth authorization callback when auth is required")
            .outputKey("oauthApproval")
            .async(true)
            .responseProvider(scope -> {
                if (!authRequiredIndicator.isAuthRequired()) {
                    Log.debug("QlawkusAgentWorkflow: HITL gate skipped, auth not required");
                    return "not-needed";
                }
                authRequiredIndicator.reset();
                Log.infof("QlawkusAgentWorkflow: HITL gate activated, waiting for OAuth callback (memoryId=%s)", scope.memoryId());
                return new PendingResponse<>(OAUTH_RESPONSE_ID);
            })
            .build();

        workflow = AgenticServices.loopBuilder()
            .subAgents(chatAgent, oauthGate)
            .maxIterations(3)
            .outputKey("chatResponse")
            .build();

        Log.info("QlawkusAgentWorkflow: initialized with chatAgent + conditional OAuth HITL gate");
    }

    public String invoke(String memoryId, String message) {
        Map<String, Object> input = Map.of(
                "input", Map.of("message", message),
                "memoryId", memoryId
        );
        try {
            ResultWithAgenticScope<String> result = workflow.invokeWithAgenticScope(input);
            return result != null ? result.result() : null;
        } catch (AgentInvocationException e) {
            return handleOutputParsingException(e);
        }
    }

    private String handleOutputParsingException(Exception wrapper) {
        OutputParsingException ope = findOutputParsingException(wrapper);
        if (ope != null) {
            Log.debugf("QlawkusAgentWorkflow: caught OutputParsingException (UntypedAgent returns Object, LLM gave free text), extracting raw response");
            return extractTextFromParsingException(ope);
        }
        if (wrapper instanceof RuntimeException re) throw re;
        throw new RuntimeException(wrapper);
    }

    private OutputParsingException findOutputParsingException(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof OutputParsingException ope) return ope;
            current = current.getCause();
        }
        return null;
    }

    public ResultWithAgenticScope<String> invokeWithScope(String memoryId, String message) {
        Map<String, Object> input = Map.of(
            "input", Map.of("message", message),
            "memoryId", memoryId
        );
        return workflow.invokeWithAgenticScope(input);
    }

    public AgenticScope getAgenticScope(String memoryId) {
        if (workflow instanceof AgenticScopeAccess access) {
            return access.getAgenticScope(memoryId);
        }
        return null;
    }

    public boolean completeOAuthApproval(String memoryId, String approvalValue) {
        AgenticScope scope = getAgenticScope(memoryId);
        if (scope == null) {
            Log.warnf("QlawkusAgentWorkflow: no scope found for memoryId=%s to complete OAuth", memoryId);
            return false;
        }
        boolean completed = scope.completePendingResponse(OAUTH_RESPONSE_ID, approvalValue);
        if (completed) {
            Log.infof("QlawkusAgentWorkflow: OAuth approval completed for memoryId=%s", memoryId);
        } else {
            Log.warnf("QlawkusAgentWorkflow: no pending OAuth response to complete for memoryId=%s, pendingIds=%s",
                memoryId, scope.pendingResponseIds());
        }
        return completed;
    }

    public boolean hasPendingOAuth(String memoryId) {
        AgenticScope scope = getAgenticScope(memoryId);
        return scope != null && !scope.pendingResponseIds().isEmpty();
    }

    public void storeDeliveryInfo(String memoryId, String providerId, String chatId) {
        AgenticScope scope = getAgenticScope(memoryId);
        if (scope != null) {
            scope.writeState(DELIVERY_PROVIDER_KEY, providerId);
            scope.writeState(DELIVERY_CHAT_KEY, chatId);
        }
    }

    public DeliveryInfo getDeliveryInfo(String memoryId) {
        AgenticScope scope = getAgenticScope(memoryId);
        if (scope == null) return null;
        String providerId = scope.readState(DELIVERY_PROVIDER_KEY, null);
        String chatId = scope.readState(DELIVERY_CHAT_KEY, null);
        if (providerId == null || chatId == null) return null;
        return new DeliveryInfo(providerId, chatId);
    }

    public void evictScope(String memoryId) {
        if (workflow instanceof AgenticScopeAccess access) {
            access.evictAgenticScope(memoryId);
        }
    }

    private String extractTextFromParsingException(OutputParsingException e) {
        String message = e.getMessage();
        if (message == null) return null;

        int jsonStart = message.indexOf("```json");
        if (jsonStart >= 0) {
            int jsonEnd = message.indexOf("```", jsonStart + 7);
            if (jsonEnd > jsonStart) {
                return message.substring(jsonStart + 7, jsonEnd).trim();
            }
        }

        String prefix = "Failed to parse \"";
        int start = message.indexOf(prefix);
        if (start >= 0) {
            start += prefix.length();
            int base64Marker = message.indexOf("\" (base64:", start);
            if (base64Marker > start) {
                return message.substring(start, base64Marker);
            }
            int intoMarker = message.indexOf("\" into ", start);
            if (intoMarker > start) {
                return message.substring(start, intoMarker);
            }
        }

        return message;
    }

    public record DeliveryInfo(String providerId, String chatId) {}
}
