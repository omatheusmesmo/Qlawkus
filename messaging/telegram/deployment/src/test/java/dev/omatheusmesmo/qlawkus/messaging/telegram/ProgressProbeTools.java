package dev.omatheusmesmo.qlawkus.messaging.telegram;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only tools named like the ones the progress tests script. Each returns a marker the tests
 * look for in the platform bodies, so a leaked tool result is detectable. The delivery probe records
 * what the tool sees, which is how a regression of the turn's thread-bound context shows up.
 */
@QlawTool
@ApplicationScoped
public class ProgressProbeTools {

    static final String RESULT = "RESULT-BODY";

    static final List<String> DELIVERY_SEEN = new CopyOnWriteArrayList<>();

    @Inject
    AgentDeliveryContext deliveryContext;

    @Tool("Query the metrics of the agent")
    public String queryMetrics(@P("PromQL query") String query) {
        return RESULT;
    }

    @Tool("Search the web")
    public String webSearch(@P("search text") String q) {
        return RESULT;
    }

    @Tool("Run a shell command")
    public String shellExec(@P("command") String token) {
        return RESULT;
    }

    @Tool("Report the delivery context the tool sees")
    public String recordDeliveryContext() {
        DELIVERY_SEEN.add(deliveryContext.chatId());
        return RESULT;
    }
}
