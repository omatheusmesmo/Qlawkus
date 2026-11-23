package dev.omatheusmesmo.qlawkus.heartbeat;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
@IfBuildProperty(name = "qlawkus.heartbeat.typed-decision-model", stringValue = "true")
class TypedHeartbeatDecisionModel implements HeartbeatDecisionModel {

    private final DecisionModel model;

    @Inject
    TypedHeartbeatDecisionModel(@ModelName("heartbeat") DecisionModel model) {
        this.model = model;
    }

    @Override
    public HeartbeatDecision decide(Map<String, Object> fields) {
        var choices = ChoiceQuestion.builder().text("""
                Which permitted heartbeat action matches the recorded owner instructions and observed state?
                If useful work is unclear, choose NO_OP. Choose a notification only when
                notificationAvailable is true and there is a matching problem.
                """);
        for (HeartbeatDecision.Action action : HeartbeatDecision.Action.values()) {
            choices.option(action.name(), action.description);
        }
        var reply = model.decide(DecisionRequest.builder().inputFields(fields).question("action", choices.build()).build());
        String action = reply.isRefused("action") ? "NO_OP" : reply.choice("action").value();
        return HeartbeatDecision.from(action, reply.tokenUsage());
    }
}
