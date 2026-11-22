package dev.omatheusmesmo.qlawkus.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** One sanitized observation. The excerpt is absent unless explicitly opted in. */
public record DebugEntry(long id, Instant occurredAt, String kind, UUID runId, UUID invocationId, String traceId,
                         String taskId, String conversationId, String subject, String callId, String outcome,
                         JsonNode detail, String excerpt) {}
