package dev.omatheusmesmo.qlawkus.it.cognition;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingBackfill;
import dev.omatheusmesmo.qlawkus.store.pg.EmbeddingRepository;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the second half of a model change: once the migration has cleared the vectors the new model
 * cannot compare with, the backfill rebuilds them from the stored text, so no fact is lost and none
 * stays unreachable by retrieval.
 */
@QuarkusTest
@ConnectWireMock
class EmbeddingBackfillTest {

    WireMock wiremock;

    @Inject
    EmbeddingBackfill backfill;

    @Inject
    FactStore factStore;

    @Inject
    DataSource dataSource;

    @Inject
    EmbeddingRepository embeddingRepository;

    @ConfigProperty(name = "quarkus.langchain4j.pgvector.dimension")
    int dimension;

    @BeforeEach
    void stubTheModel() {
        QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
    }

    @AfterEach
    @Transactional
    void cleanup() {
        embeddingRepository.deleteAll();
    }

    @Test
    void aClearedVectorIsRebuiltFromItsTextAtTheConfiguredDimension() throws SQLException {
        UUID id = insertWithoutVector("the owner keeps bees on the roof");

        assertEquals(1, backfill.reembedMissing());

        assertEquals(dimension, dimensionsOf(id), "the rebuilt vector must be comparable with new ones");
        assertEquals(0, backfill.reembedMissing(), "a second pass has nothing left to do");
    }

    @Test
    void retrievalKeepsWorkingWhileSomeVectorsAreStillMissing() throws SQLException {
        insertWithoutVector("a fact waiting to be re-embedded");
        factStore.store("the owner drinks green tea", Map.of("source", "remember-tool"));

        assertDoesNotThrow(() -> factStore.search("what does the owner drink", 5, 0.0),
                "between the migration and the backfill the agent must still answer");
    }

    private UUID insertWithoutVector(String text) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("INSERT INTO embeddings"
                        + " (embedding_id, embedding, text, metadata) VALUES (?, NULL, ?, ?::jsonb)")) {
            statement.setObject(1, id);
            statement.setString(2, text);
            statement.setString(3, "{\"source\":\"remember-tool\",\"factHash\":\"" + id + "\"}");
            statement.executeUpdate();
        }
        return id;
    }

    private int dimensionsOf(UUID id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT vector_dims(embedding) FROM embeddings WHERE embedding_id = ?")) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
