package dev.omatheusmesmo.qlawkus.langfuse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

class LangfuseMetadataExporterTest {

    /**
     * OpenTelemetry SDK 1.67 hands out non-recording spans from a provider with no span processor,
     * so the test registers one that does nothing.
     */
    private static final SpanProcessor RECORDING = new SpanProcessor() {
        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
        }

        @Override
        public boolean isStartRequired() {
            return true;
        }

        @Override
        public void onEnd(ReadableSpan span) {
        }

        @Override
        public boolean isEndRequired() {
            return true;
        }
    };

    @Test
    void keepsOnlyMetadataInTheLangfuseCopy() {
        var resource = Resource.create(Attributes.builder()
                .put("service.name", "qlawkus")
                .put("host.name", "private-host")
                .build());
        try (var provider = SdkTracerProvider.builder().setResource(resource).addSpanProcessor(RECORDING).build()) {
            var tracer = provider.get("test");
            var turn = tracer.spanBuilder("private span name").startSpan();
            var tool = tracer.spanBuilder("private tool span name")
                    .setParent(Context.root().with(turn))
                    .addLink(turn.getSpanContext(), Attributes.of(AttributeKey.stringKey("link"), "private link"))
                    .startSpan();
            tool.setAttribute("gen_ai.operation.name", "execute_tool");
            tool.setAttribute("gen_ai.tool.name", "lookup");
            tool.setAttribute("gen_ai.tool.call.arguments", "private arguments");
            tool.setAttribute("gen_ai.tool.call.result", "private tool result");
            tool.setAttribute("gen_ai.input.messages", "private prompt");
            tool.setAttribute("gen_ai.output.messages", "private response");
            tool.setAttribute("gen_ai.usage.input_tokens", 10L);
            tool.setAttribute("gen_ai.conversation.id", "private conversation");
            tool.setAttribute("session.id", "raw session id");
            tool.setAttribute("user.id", "raw owner id");
            tool.recordException(new IllegalStateException("private exception"));
            tool.setStatus(StatusCode.ERROR, "private error description");
            tool.end();
            turn.end();

            SpanData local = ((ReadableSpan) tool).toSpanData();
            SpanData remote = LangfuseMetadataExporter.metadata(local);
            assertEquals(local.getTraceId(), remote.getTraceId());
            assertEquals(local.getParentSpanId(), remote.getParentSpanId());
            assertEquals(local.getStartEpochNanos(), remote.getStartEpochNanos());
            assertEquals("tool lookup", remote.getName());
            assertEquals(Attributes.builder()
                    .put("gen_ai.operation.name", "execute_tool")
                    .put("gen_ai.tool.name", "lookup")
                    .put("gen_ai.usage.input_tokens", 10L)
                    .put("langfuse.observation.type", "tool")
                    .put("session.id", LangfuseMetadataExporter.sessionId("private conversation"))
                    .put("user.id", "owner")
                    .build(), remote.getAttributes());
            assertEquals(StatusCode.ERROR, remote.getStatus().getStatusCode());
            assertTrue(remote.getStatus().getDescription().isEmpty());
            assertTrue(remote.getEvents().isEmpty());
            assertTrue(remote.getLinks().isEmpty());
            assertEquals("qlawkus", remote.getResource().getAttribute(AttributeKey.stringKey("service.name")));
            assertNull(remote.getResource().getAttribute(AttributeKey.stringKey("host.name")));
            assertEquals("private arguments", local.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.call.arguments")));
            assertEquals(1, local.getEvents().size());
        }
    }
}
