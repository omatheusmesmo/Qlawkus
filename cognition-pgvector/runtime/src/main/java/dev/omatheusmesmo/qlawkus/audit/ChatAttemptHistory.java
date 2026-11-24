package dev.omatheusmesmo.qlawkus.audit;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.omatheusmesmo.qlawkus.logging.FailureChain;
import dev.omatheusmesmo.qlawkus.model.WorkloadContext;
import dev.omatheusmesmo.qlawkus.task.TaskAudit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Provider calls include retries, fallbacks and direct decision calls outside an AI Service. */
@ApplicationScoped
public class ChatAttemptHistory implements ChatModelListener {
    private static final Object KEY = new Object();

    private record Attempt(String id, UUID runId, String traceId, String workload, String provider, long start) {}

    @Inject DebugHistoryConfig config;
    @Inject PgDebugJournal journal;
    @Inject ObjectMapper json;

    @Override
    public void onRequest(ChatModelRequestContext context) {
        if (!config.enabled()) return;
        var attempt = new Attempt(UUID.randomUUID().toString(), TaskAudit.currentRun(), DebugHistoryRecorder.traceId(),
                WorkloadContext.current(), String.valueOf(context.modelProvider()), System.nanoTime());
        context.attributes().put(KEY, attempt);
        var detail = detail(attempt);
        DebugHistoryRecorder.requestSize(context.chatRequest(), detail);
        record(attempt, context.chatRequest().modelName(), "STARTED", detail);
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        var attempt = take(context.attributes());
        if (attempt == null) return;
        var detail = detail(attempt);
        detail.put("durationMs", elapsed(attempt));
        DebugHistoryRecorder.usage(context.chatResponse(), detail);
        record(attempt, context.chatResponse().modelName(), "COMPLETED", detail);
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        var attempt = take(context.attributes());
        if (attempt == null) return;
        var detail = detail(attempt);
        detail.put("durationMs", elapsed(attempt));
        Throwable error = context.error();
        detail.put("errorType", FailureChain.root(error).getClass().getSimpleName());
        FailureChain.first(error, HttpException.class).ifPresent(http -> detail.put("httpStatus", http.statusCode()));
        record(attempt, context.chatRequest().modelName(), "ERROR", detail);
    }

    private static Attempt take(Map<Object, Object> attributes) {
        return attributes.remove(KEY) instanceof Attempt attempt ? attempt : null;
    }

    private ObjectNode detail(Attempt attempt) {
        return json.createObjectNode().put("provider", attempt.provider()).put("workload", attempt.workload());
    }

    private static long elapsed(Attempt attempt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - attempt.start());
    }

    private void record(Attempt attempt, String model, String status, ObjectNode detail) {
        String trace = attempt.traceId() != null ? attempt.traceId() : DebugHistoryRecorder.traceId();
        journal.enqueue(new DebugEntry(0, Instant.now(), "model.attempt", attempt.runId(), null,
                trace, null, null, DebugHistoryRecorder.bounded(model), attempt.id(), status, detail, null));
    }
}
