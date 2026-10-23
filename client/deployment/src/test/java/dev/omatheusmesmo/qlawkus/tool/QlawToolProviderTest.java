package dev.omatheusmesmo.qlawkus.tool;

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.metrics.MeteredToolExecutor;
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

    @Test
    void provideTools_returnsEmptyWhenNoExtensionTools() {
        QlawToolProvider emptyProvider = new QlawToolProvider();
        @SuppressWarnings("unchecked")
        Instance<Object> unsatisfied = Mockito.mock(Instance.class);
        Mockito.when(unsatisfied.isUnsatisfied()).thenReturn(true);

        emptyProvider.extensionTools = unsatisfied;

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
}
