package dev.omatheusmesmo.qlawkus.tracing;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the span every {@code @QlawTool} call produces, which is what turns a slow ReAct turn into a
 * flame graph instead of a mystery. The name and attributes deliberately match the span upstream
 * {@code ToolSpanWrapper} gives the tools declared on the AI service, so both kinds of tool read the
 * same in a trace and one query finds them all.
 */
class TracedToolExecutorTest {

    private static final ToolExecutionRequest REQUEST = ToolExecutionRequest.builder()
            .id("call-7").name("searchSkillHub").arguments("{\"query\":\"owner secret\"}").build();

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider provider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    private final Tracer tracer = provider.get("test");

    @AfterEach
    void close() {
        provider.close();
    }

    @Test
    void aToolCallBecomesAChildSpanOfTheTurn() {
        RecordingTool tool = new RecordingTool(ToolExecutionResult.builder().resultText("3 hits").build());
        Span turn = tracer.spanBuilder("turn").startSpan();
        try (Scope ignored = turn.makeCurrent()) {
            traced(tool, false, false).executeWithContext(REQUEST, null);
        } finally {
            turn.end();
        }

        SpanData span = toolSpan();
        assertEquals("langchain4j.tools.searchSkillHub", span.getName());
        assertEquals(turn.getSpanContext().getSpanId(), span.getParentSpanId(),
                "a tool span outside its turn cannot be read as part of the ReAct loop");
        assertEquals("execute_tool", attribute(span, "gen_ai.operation.name"));
        assertEquals("searchSkillHub", attribute(span, "gen_ai.tool.name"));
        assertEquals("call-7", attribute(span, "gen_ai.tool.call.id"));
        assertEquals("function", attribute(span, "gen_ai.tool.type"));
        assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
    }

    @Test
    void workInsideTheToolIsParentedUnderItsSpan() {
        RecordingTool tool = new RecordingTool(ToolExecutionResult.builder().resultText("ok").build());

        traced(tool, false, false).executeWithContext(REQUEST, null);

        assertEquals(toolSpan().getSpanId(), tool.currentSpanId,
                "an HTTP call a tool makes must nest under the tool, or the stall cannot be located");
    }

    @Test
    void argumentsAndResultStayOutOfTheSpanUnlessEnabled() {
        traced(new RecordingTool(ToolExecutionResult.builder().resultText("3 hits").build()), false, false)
                .executeWithContext(REQUEST, null);

        SpanData span = toolSpan();
        assertNull(attribute(span, "gen_ai.tool.call.arguments"),
                "arguments can carry what the owner said, so they are opt-in like upstream");
        assertNull(attribute(span, "gen_ai.tool.call.result"));
    }

    @Test
    void argumentsAndResultAreRecordedWhenEnabled() {
        traced(new RecordingTool(ToolExecutionResult.builder().resultText("3 hits").build()), true, true)
                .executeWithContext(REQUEST, null);

        SpanData span = toolSpan();
        assertEquals("{\"query\":\"owner secret\"}", attribute(span, "gen_ai.tool.call.arguments"));
        assertEquals("3 hits", attribute(span, "gen_ai.tool.call.result"));
    }

    @Test
    void aToolThatThrowsMarksItsSpanAndStillFails() {
        IllegalStateException failure = new IllegalStateException("registry unreachable");
        ToolExecutor tool = new RecordingTool(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> traced(tool, false, false).executeWithContext(REQUEST, null)));

        SpanData span = toolSpan();
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals("exception", span.getEvents().get(0).getName());
    }

    @Test
    void aToolThatReportsAnErrorMarksItsSpan() {
        traced(new RecordingTool(ToolExecutionResult.builder().isError(true).resultText("bad args").build()),
                false, false).executeWithContext(REQUEST, null);

        assertEquals(StatusCode.ERROR, toolSpan().getStatus().getStatusCode(),
                "a tool that returns an error instead of throwing is still the call that went wrong");
    }

    @Test
    void thePlainExecutePathIsTracedToo() {
        RecordingTool tool = new RecordingTool(ToolExecutionResult.builder().resultText("ok").build());

        assertEquals("ok", traced(tool, false, false).execute(REQUEST, "memory-1"));

        assertEquals("langchain4j.tools.searchSkillHub", toolSpan().getName());
    }

    /**
     * The {@code observability} extension silences {@code io.opentelemetry.usage} because Quarkus
     * itself misuses the API at boot. That would hide a misuse of ours too, so this keeps the detector
     * pointed at the one place this project drives the trace API, on a recording and a no-op tracer.
     */
    @Test
    void aToolCallReportsNoOpenTelemetryApiUsageIssue() {
        Logger usage = Logger.getLogger("io.opentelemetry.usage");
        Level previous = usage.getLevel();
        List<LogRecord> issues = new CopyOnWriteArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                issues.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        usage.setLevel(Level.ALL);
        usage.addHandler(capture);
        try {
            for (Tracer each : List.of(tracer, TracerProvider.noop().get("test"))) {
                new TracedToolExecutor(REQUEST.name(),
                        new RecordingTool(ToolExecutionResult.builder().resultText("ok").build()), each, true, true)
                        .executeWithContext(REQUEST, null);
            }
        } finally {
            usage.removeHandler(capture);
            usage.setLevel(previous);
        }

        assertEquals(List.of(), issues.stream().map(LogRecord::getMessage).toList(),
                "a usage issue of ours would be hidden by the logger the observability extension silences");
    }

    private TracedToolExecutor traced(ToolExecutor tool, boolean arguments, boolean result) {
        return new TracedToolExecutor(REQUEST.name(), tool, tracer, arguments, result);
    }

    private SpanData toolSpan() {
        List<SpanData> spans = exporter.getFinishedSpanItems().stream()
                .filter(span -> span.getName().startsWith("langchain4j.tools."))
                .toList();
        assertEquals(1, spans.size(), "one tool call, one span: " + spans);
        return spans.get(0);
    }

    private static String attribute(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.stringKey(key));
    }

    /** A tool that answers with a fixed result or failure and remembers which span was current. */
    private static final class RecordingTool implements ToolExecutor {

        private final ToolExecutionResult result;
        private final RuntimeException failure;
        String currentSpanId;

        RecordingTool(ToolExecutionResult result) {
            this.result = result;
            this.failure = null;
        }

        RecordingTool(RuntimeException failure) {
            this.result = null;
            this.failure = failure;
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            return executeWithContext(request, null).resultText();
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            currentSpanId = Span.current().getSpanContext().getSpanId();
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
