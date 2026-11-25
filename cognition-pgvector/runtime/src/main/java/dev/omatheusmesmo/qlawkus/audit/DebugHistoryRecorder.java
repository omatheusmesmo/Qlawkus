package dev.omatheusmesmo.qlawkus.audit;

import dev.omatheusmesmo.qlawkus.store.pg.CognitionBackendState;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.agent.AgentTurn;
import dev.omatheusmesmo.qlawkus.agent.QlawkusAgent;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import dev.omatheusmesmo.qlawkus.task.TaskAudit;
import dev.omatheusmesmo.qlawkus.task.TaskAuditEvent;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import io.opentelemetry.api.trace.Span;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Snapshots only the fields the journal understands, on the thread that receives the event. */
@ApplicationScoped
public class DebugHistoryRecorder {
    private static final Set<String> AGENTS = Set.of(AgentService.class.getName(), AgentService.class.getSimpleName(),
            QlawkusAgent.class.getName(), QlawkusAgent.class.getSimpleName());
    private static final String RUN_PARAMETER = DebugHistoryRecorder.class.getName() + ".runId";
    private static final int EXCERPT_LIMIT = 2048;

    @Inject DebugHistoryConfig config;
    @Inject CognitionBackendState backend;
    @Inject PgDebugJournal journal;
    @Inject ObjectMapper json;
    @Inject SecretRedactor redactor;

    void task(@Observes TaskAuditEvent event) {
        if (!config.enabled() || !backend.usesPostgres()) return;
        var detail = json.createObjectNode();
        if (event.nextFire() != null) detail.put("nextFire", event.nextFire().toString());
        if (event.errorType() != null) detail.put("errorType", event.errorType());
        if (event.httpStatus() != null) detail.put("httpStatus", event.httpStatus());
        if (event.acceptedParts() != null) detail.put("acceptedParts", event.acceptedParts());
        if (event.totalParts() != null) detail.put("totalParts", event.totalParts());
        journal.enqueue(new DebugEntry(0, Instant.now(), "task", event.runId(), null, traceId(), event.taskId(),
                null, bounded(event.provider()), null, event.stage().name(), detail, null));
    }

    void service(@Observes AiServiceEvent event) {
        if (!config.enabled() || !backend.usesPostgres()) return;
        InvocationContext context = event.invocationContext();
        if (context == null || context.interfaceName() == null || !AGENTS.contains(context.interfaceName())) return;
        try {
            recordService(event, context);
        } catch (RuntimeException failure) {
            // Observers run on the agent path. A journal error must never change the model outcome.
            Log.debugf("Could not capture AI debug event (%s)", failure.getClass().getSimpleName());
        }
    }

    private void recordService(AiServiceEvent event, InvocationContext context) {
        var detail = json.createObjectNode();
        switch (event) {
            case AiServiceStartedEvent started -> {
                if (TaskAudit.currentRun() != null && context.invocationParameters() != null) {
                    context.invocationParameters().put(RUN_PARAMETER, TaskAudit.currentRun());
                }
                var turn = AgentTurn.current();
                if (turn != null) detail.put("replyLanguage", turn.replyStyle().language());
                var user = context.originalUserMessage();
                if (user == null) user = started.userMessage();
                String text = config.captureText() && TaskAudit.currentRun() == null && user != null && user.hasSingleText()
                        ? user.singleText() : null;
                entry(context, "turn", context.methodName(), null, "STARTED", detail, excerpt(text));
            }
            case AiServiceRequestIssuedEvent request -> {
                requestSize(request.request(), detail);
                entry(context, "model.request", request.request().modelName(), null, null, detail, null);
            }
            case AiServiceResponseReceivedEvent received -> {
                var response = received.response();
                usage(response, detail);
                var message = response.aiMessage();
                entry(context, "model.response", response.modelName(), response.id(), "OK", detail,
                        config.captureText() && message != null ? excerpt(message.text()) : null);
                if (message != null && message.hasToolExecutionRequests()) {
                    for (var tool : message.toolExecutionRequests()) {
                        entry(context, "tool", tool.name(), tool.id(), "REQUESTED", json.createObjectNode(), null);
                    }
                }
            }
            case ToolExecutedEvent tool ->
                entry(context, "tool", tool.request().name(), tool.request().id(),
                        tool.isError() ? "ERROR" : "EXECUTED", detail, null);
            case AiServiceCompletedEvent ignored ->
                entry(context, "turn", context.methodName(), null, "COMPLETED", detail, null);
            case AiServiceCancelledEvent ignored ->
                entry(context, "turn", context.methodName(), null, "CANCELLED", detail, null);
            case AiServiceErrorEvent error -> {
                detail.put("errorType", error.error().getClass().getSimpleName());
                entry(context, "turn", context.methodName(), null, "ERROR", detail, null);
            }
            default -> { }
        }
    }

    private void entry(InvocationContext context, String kind, String subject, String callId, String outcome,
                       ObjectNode detail, String text) {
        UUID run = context.invocationParameters() == null ? null : context.invocationParameters().get(RUN_PARAMETER);
        detail.put("workload", WorkloadContext.current());
        Object memoryId = context.chatMemoryId();
        journal.enqueue(new DebugEntry(0, Instant.now(), kind, run != null ? run : TaskAudit.currentRun(),
                context.invocationId(), traceId(), null, memoryId == null ? null : bounded(memoryId.toString()),
                bounded(subject), bounded(callId), outcome, detail, text));
    }

    private String excerpt(String text) {
        // Large text is skipped before redaction so journaling doesn't add unbounded work.
        if (text == null || text.length() > 8192) return null;
        String safe = redactor.redact(text).replace("\0", "");
        int end = Math.min(safe.length(), EXCERPT_LIMIT);
        if (end > 0 && Character.isHighSurrogate(safe.charAt(end - 1))) end--;
        return safe.substring(0, end);
    }

    static String traceId() {
        var context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : null;
    }

    static String bounded(String text) {
        if (text == null) return null;
        String value = text.replaceAll("[\\p{Cntrl}]", "");
        int end = Math.min(200, value.length());
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    static void requestSize(ChatRequest request, ObjectNode detail) {
        detail.put("messages", request.messages().size());
        var specs = request.toolSpecifications();
        detail.put("toolDefinitions", specs == null ? 0 : specs.size());
    }

    static void usage(ChatResponse response, ObjectNode detail) {
        if (response.tokenUsage() != null) {
            detail.put("inputTokens", response.tokenUsage().inputTokenCount());
            detail.put("outputTokens", response.tokenUsage().outputTokenCount());
        }
        if (response.finishReason() != null) detail.put("finishReason", response.finishReason().name());
    }
}
