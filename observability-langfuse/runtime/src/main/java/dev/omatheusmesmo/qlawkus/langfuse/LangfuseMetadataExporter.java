package dev.omatheusmesmo.qlawkus.langfuse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/** Keeps the Langfuse export copy independent of any local trace content settings. */
final class LangfuseMetadataExporter implements SpanExporter {

    static final AttributeKey<String> TURN_SESSION = AttributeKey.stringKey("qlawkus.langfuse.session");
    static final AttributeKey<String> WORKLOAD = AttributeKey.stringKey("qlawkus.workload");
    private static final AttributeKey<String> SESSION_ID = AttributeKey.stringKey("session.id");
    private static final AttributeKey<List<String>> TRACE_TAGS = AttributeKey.stringArrayKey("langfuse.trace.tags");
    private static final AttributeKey<String> CONVERSATION_ID = AttributeKey.stringKey("gen_ai.conversation.id");
    private static final AttributeKey<String> OPERATION = AttributeKey.stringKey("gen_ai.operation.name");
    private static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");

    private static final Set<String> METADATA = Set.of(
            "gen_ai.operation.name", "gen_ai.provider.name", "gen_ai.system", "gen_ai.request.model",
            "gen_ai.response.model", "gen_ai.response.finish_reasons", "gen_ai.usage.input_tokens",
            "gen_ai.usage.output_tokens", "gen_ai.tool.name", "gen_ai.tool.type", "gen_ai.tool.call.id",
            "langfuse.environment", "qlawkus.workload");
    private static final Set<String> RESOURCE_METADATA = Set.of("service.name", "service.namespace", "service.version");

    private final SpanExporter delegate;

    LangfuseMetadataExporter(SpanExporter delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        return delegate.export(spans.stream().map(LangfuseMetadataExporter::metadata).toList());
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    static String sessionId(String conversation) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    conversation.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    static SpanData metadata(SpanData span) {
        var original = span.getAttributes();
        var allowed = original.toBuilder().removeIf(key -> !METADATA.contains(key.getKey()));
        String session = original.get(TURN_SESSION);
        String conversation = original.get(CONVERSATION_ID);
        if (session != null || (conversation != null && !conversation.isBlank())) {
            allowed.put(SESSION_ID, session != null ? session : sessionId(conversation)).put("user.id", "owner");
        }
        if (original.get(TOOL_NAME) != null) {
            allowed.put("langfuse.observation.type", "tool");
        }
        String workload = original.get(WORKLOAD);
        if (workload != null) {
            allowed.put(TRACE_TAGS, List.of(workload));
        }
        return new MetadataSpan(span, allowed.build());
    }

    private static final class MetadataSpan extends DelegatingSpanData {

        private final Attributes metadata;

        private MetadataSpan(SpanData delegate, Attributes metadata) {
            super(delegate);
            this.metadata = metadata;
        }

        @Override
        public Attributes getAttributes() {
            return metadata;
        }

        @Override
        public String getName() {
            String tool = metadata.get(TOOL_NAME);
            String operation = metadata.get(OPERATION);
            if (tool != null) {
                return "tool " + tool;
            }
            return operation != null ? operation : "qlawkus turn";
        }

        @Override
        public Resource getResource() {
            return Resource.create(super.getResource().getAttributes().toBuilder()
                    .removeIf(key -> !RESOURCE_METADATA.contains(key.getKey())).build());
        }

        @Override
        public List<EventData> getEvents() {
            return List.of();
        }

        @Override
        public List<LinkData> getLinks() {
            return List.of();
        }

        @Override
        public StatusData getStatus() {
            return StatusData.create(super.getStatus().getStatusCode(), "");
        }

        @Override
        public int getTotalAttributeCount() {
            return metadata.size();
        }

        @Override
        public int getTotalRecordedEvents() {
            return 0;
        }

        @Override
        public int getTotalRecordedLinks() {
            return 0;
        }
    }
}
