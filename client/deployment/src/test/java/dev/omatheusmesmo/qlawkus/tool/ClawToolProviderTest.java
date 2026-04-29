package dev.omatheusmesmo.qlawkus.tool;

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ClawToolProviderTest {

    @Inject
    ClawToolProvider provider;

    @Test
    void provideTools_returnsEmptyWhenNoExtensionTools() {
        ClawToolProvider emptyProvider = new ClawToolProvider();
        @SuppressWarnings("unchecked")
        Instance<Object> unsatisfied = Mockito.mock(Instance.class);
        Mockito.when(unsatisfied.isUnsatisfied()).thenReturn(true);

        emptyProvider.extensionTools = unsatisfied;

        ToolProviderResult result = emptyProvider.provideTools(null);

        assertTrue(result.tools().isEmpty());
    }

    @Test
    void clawToolProviderSupplier_resolvesProviderFromArc() {
        ClawToolProviderSupplier supplier = new ClawToolProviderSupplier();
        ToolProvider resolved = supplier.get();

        assertNotNull(resolved);
        assertInstanceOf(ClawToolProvider.class, resolved);
    }
}
