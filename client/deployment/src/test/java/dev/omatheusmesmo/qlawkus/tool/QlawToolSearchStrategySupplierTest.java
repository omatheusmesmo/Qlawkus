package dev.omatheusmesmo.qlawkus.tool;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.tool.search.ToolSearchStrategy;
import dev.langchain4j.service.tool.search.vector.VectorToolSearchStrategy;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import io.quarkiverse.langchain4j.RegisterAiService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QlawToolSearchStrategySupplierTest {

    @Test
    void disabledSearchDoesNotUseTheEmbeddingModel() {
        QlawToolSearchStrategySupplier supplier = supplier(false);

        assertNull(supplier.get());
        verifyNoInteractions(supplier.embeddingModel);
    }

    @Test
    void enabledSearchUsesTheExistingEmbeddingModel() {
        QlawToolSearchStrategySupplier supplier = supplier(true);

        assertInstanceOf(VectorToolSearchStrategy.class, supplier.get());
    }

    @Test
    void onlyTheAgentServiceExplicitlySelectsTheSupplier() {
        assertEquals(QlawToolSearchStrategySupplier.class,
                AgentService.class.getAnnotation(RegisterAiService.class).toolSearchStrategySupplier());
        assertFalse(ToolSearchStrategy.class.isAssignableFrom(QlawToolSearchStrategySupplier.class),
                "a global strategy bean would silently enable search on unrelated AI services");
    }

    private static QlawToolSearchStrategySupplier supplier(boolean enabled) {
        QlawToolSearchStrategySupplier supplier = new QlawToolSearchStrategySupplier();
        supplier.config = mock(AgentConfig.class, RETURNS_DEEP_STUBS);
        when(supplier.config.toolSearch().enabled()).thenReturn(enabled);
        supplier.embeddingModel = mock(EmbeddingModel.class);
        return supplier;
    }
}
