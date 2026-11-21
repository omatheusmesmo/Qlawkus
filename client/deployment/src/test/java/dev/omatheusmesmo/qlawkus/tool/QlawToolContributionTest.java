package dev.omatheusmesmo.qlawkus.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.agent.StreamedToolContext;
import dev.omatheusmesmo.qlawkus.metrics.AgentMeters;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class QlawToolContributionTest {
    @Test
    void dynamicToolsAreResolvedAgainOnEachTurn() {
        var current = new AtomicReference<>(result("mcp_first", (request, memory) -> "first"));
        var provider = provider(request -> current.get());
        assertEquals("mcp_first", provider.provideTools(null).tools().keySet().iterator().next().name());
        current.set(result("mcp_second", (request, memory) -> "second"));
        assertEquals("mcp_second", provider.provideTools(null).tools().keySet().iterator().next().name());
    }

    @Test
    void aBrokenContributorDoesNotHideOtherTools() {
        QlawToolContributor broken = request -> { throw new IllegalStateException("private endpoint detail"); };
        var provider = provider(broken, request -> result("mcp_available", (call, memory) -> "ok"));
        assertEquals(1, provider.provideTools(null).tools().size());
    }

    @Test
    void duplicateNamesCannotSilentlyRedirectCalls() {
        QlawToolContributor first = request -> result("duplicate", (call, memory) -> "first");
        QlawToolContributor second = request -> result("duplicate", (call, memory) -> "second");
        assertThrows(IllegalStateException.class, () -> provider(first, second).provideTools(null));
    }

    @Test
    void structuredExecutionPreservesContextAndErrorWhileUsingTheCommonMeters() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        var request = ToolExecutionRequest.builder().id("call-1").name("mcp_test").arguments("{}").build();
        var context = mock(InvocationContext.class);
        when(context.chatMemoryId()).thenReturn("conversation");
        var expected = ToolExecutionResult.builder().resultText("unavailable").isError(true).build();
        when(delegate.executeWithContext(request, context)).thenReturn(expected);
        var provider = provider(ignored -> result("mcp_test", delegate));
        var executor = provider.provideTools(null).tools().values().iterator().next();
        assertSame(expected, executor.executeWithContext(request, context));
        verify(provider.streamedTools).enter("conversation");
        verify(provider.meters).tool(eq("mcp_test"), any(), eq(true));
        verify(delegate).executeWithContext(request, context);
    }

    @Test
    void contributedPayloadsStayOutOfSpansEvenWhenLocalCaptureIsEnabled() {
        var exporter = InMemorySpanExporter.create();
        try (var tracing = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()) {
            var provider = provider(ignored -> result("mcp_test", (call, memory) -> "remote-private-result"));
            provider.tracer = instances(tracing.get("test"));
            provider.includeToolArguments = true;
            provider.includeToolResult = true;
            var executor = provider.provideTools(null).tools().values().iterator().next();
            assertEquals("remote-private-result", executor.execute(ToolExecutionRequest.builder()
                    .id("call").name("mcp_test").arguments("private-argument").build(), "memory"));
            var span = exporter.getFinishedSpanItems().getFirst();
            assertEquals("mcp_test", span.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.name")));
            assertNull(span.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.call.arguments")));
            assertNull(span.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.call.result")));
        }
    }

    private static ToolProviderResult result(String name, ToolExecutor executor) {
        return ToolProviderResult.builder().add(ToolSpecification.builder().name(name).description("test").build(), executor).build();
    }

    private static QlawToolProvider provider(QlawToolContributor... contributors) {
        var provider = new QlawToolProvider();
        provider.extensionTools = instances();
        provider.contributors = instances(contributors);
        provider.tracer = instances();
        provider.meters = mock(AgentMeters.class);
        provider.streamedTools = mock(StreamedToolContext.class);
        when(provider.streamedTools.enter(any())).thenReturn(StreamedToolContext.Entered.NONE);
        return provider;
    }

    @SafeVarargs
    private static <T> Instance<T> instances(T... values) {
        @SuppressWarnings("unchecked")
        Instance<T> instance = mock(Instance.class);
        when(instance.isUnsatisfied()).thenReturn(values.length == 0);
        when(instance.isResolvable()).thenReturn(values.length == 1);
        when(instance.iterator()).thenAnswer(ignored -> List.of(values).iterator());
        if (values.length > 0) {
            when(instance.get()).thenReturn(values[0]);
        }
        return instance;
    }
}
