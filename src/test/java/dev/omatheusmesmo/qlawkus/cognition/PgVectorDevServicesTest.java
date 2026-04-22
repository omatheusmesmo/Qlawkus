package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class PgVectorDevServicesTest {

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<?> embeddingStore;

    @Test
    void embeddingModel_isInjected() {
        assertNotNull(embeddingModel);
    }

    @Test
    void embeddingStore_isInjected() {
        assertNotNull(embeddingStore);
    }

    @Test
    void embeddingModel_generatesVector() {
        Embedding embedding = embeddingModel.embed("hello").content();

        assertNotNull(embedding);
        assertNotNull(embedding.vector());
    }
}
