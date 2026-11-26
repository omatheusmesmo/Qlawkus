package dev.omatheusmesmo.qlawkus.tool;

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.metrics.MeteredToolExecutor;
import dev.omatheusmesmo.qlawkus.tool.quarantine.ToolQuarantine;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class QlawToolProviderTest {

    @Inject
    QlawToolProvider provider;

    @Inject
    ToolQuarantine quarantine;

    @Test
    void provideTools_returnsEmptyWhenNoExtensionTools() {
        QlawToolProvider emptyProvider = new QlawToolProvider();
        @SuppressWarnings("unchecked")
        Instance<Object> unsatisfied = Mockito.mock(Instance.class);
        Mockito.when(unsatisfied.isUnsatisfied()).thenReturn(true);

        emptyProvider.extensionTools = unsatisfied;
        @SuppressWarnings("unchecked")
        Instance<QlawToolContributor> noContributors = Mockito.mock(Instance.class);
        Mockito.when(noContributors.isUnsatisfied()).thenReturn(true);
        emptyProvider.contributors = noContributors;

        ToolProviderResult result = emptyProvider.provideTools(null);

        assertTrue(result.tools().isEmpty());
    }

    @Test
    void clawToolProviderSupplier_resolvesProviderFromArc() {
        QlawToolProviderSupplier supplier = new QlawToolProviderSupplier();
        ToolProvider resolved = supplier.get();

        assertNotNull(resolved);
        assertInstanceOf(QlawToolProvider.class, resolved);
    }

    @Test
    void toolsAreMeteredButNotTracedWithoutATracer() {
        ToolProviderResult result = provider.provideTools(null);

        assertFalse(result.tools().isEmpty(), "the client ships its own tools, so there is one to check");
        result.tools().values().forEach(executor -> assertInstanceOf(MeteredToolExecutor.class, executor,
                "with no tracing extension present there is nothing to export a span to"));
    }

    @Test
    void aQuarantinedToolLeavesTheCatalogUntilItIsReleased() {
        String name = provider.provideTools(null).tools().keySet().stream().map(spec -> spec.name()).sorted()
                .findFirst().orElseThrow();
        try {
            for (int i = 0; i < 5; i++) {
                quarantine.recordFailure(name);
            }

            assertFalse(names().contains(name), "the model must not be offered a tool that keeps failing");

            quarantine.release(name);

            assertTrue(names().contains(name));
        } finally {
            quarantine.release(name);
        }
    }

    private java.util.Set<String> names() {
        return provider.provideTools(null).tools().keySet().stream().map(spec -> spec.name())
                .collect(java.util.stream.Collectors.toSet());
    }
}
