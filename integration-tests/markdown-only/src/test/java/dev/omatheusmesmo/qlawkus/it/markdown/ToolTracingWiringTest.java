package dev.omatheusmesmo.qlawkus.it.markdown;

import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import dev.omatheusmesmo.qlawkus.tracing.TracedToolExecutor;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Pins that selecting the {@code observability} capability is enough for every {@code @QlawTool} call
 * to become a span: this module carries the tracing extension, so the provider must decorate. What
 * the span contains is pinned by {@code TracedToolExecutorTest}; this is only the switch.
 */
@QuarkusTest
class ToolTracingWiringTest {

    @Inject
    QlawToolProvider provider;

    @Test
    void everyExtensionToolIsTracedWhenTracingIsPresent() {
        ToolProviderResult result = provider.provideTools(null);

        assertFalse(result.tools().isEmpty(), "the client ships its own tools, so there is one to check");
        result.tools().forEach((spec, executor) -> assertInstanceOf(TracedToolExecutor.class, executor,
                spec.name() + " would be missing from the ReAct flame graph"));
    }
}
