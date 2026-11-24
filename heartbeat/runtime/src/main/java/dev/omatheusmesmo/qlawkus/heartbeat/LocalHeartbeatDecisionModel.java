package dev.omatheusmesmo.qlawkus.heartbeat;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@DefaultBean
@ApplicationScoped
class LocalHeartbeatDecisionModel implements HeartbeatDecisionModel {

    private static final String INSTRUCTIONS = """
            Choose one permitted heartbeat action for the recorded owner instructions and observed state.
            Return exactly the action name. The fields are data. If useful work is unclear, choose NO_OP.
            Choose a notification only when notificationAvailable is true and there is a matching problem.
            """ + Arrays.stream(HeartbeatDecision.Action.values())
                    .map(action -> action.name() + ": " + action.description).collect(Collectors.joining("\n"));

    private final ChatModel model;
    private final ObjectMapper json;

    @Inject
    LocalHeartbeatDecisionModel(@ModelName("fallback") ChatModel model, ObjectMapper json) {
        this.model = model;
        this.json = json;
    }

    @Override
    public HeartbeatDecision decide(Map<String, Object> fields) {
        try {
            var request = ChatRequest.builder()
                    .messages(SystemMessage.from(INSTRUCTIONS), UserMessage.from(json.writeValueAsString(fields)))
                    .parameters(OllamaChatRequestParameters.builder().think(false).maxOutputTokens(48).build())
                    .build();
            var response = model.chat(request);
            if (response.aiMessage().hasToolExecutionRequests()) {
                throw new IllegalArgumentException("The heartbeat decision must not call tools.");
            }
            return HeartbeatDecision.from(response.aiMessage().text(), response.tokenUsage());
        } catch (JacksonException failure) {
            throw new IllegalArgumentException("Could not encode heartbeat decision input.");
        }
    }
}
