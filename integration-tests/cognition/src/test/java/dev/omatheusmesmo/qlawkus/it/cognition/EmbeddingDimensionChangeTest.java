package dev.omatheusmesmo.qlawkus.it.cognition;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Boots the agent with an embedding dimension other than the 1024 that V4 creates, which is the
 * situation #357 put every NVIDIA deployment in. The schema has to end up holding the configured
 * dimension, or the first stored vector fails. The test default kind is also 1024, so only a profile
 * that changes the dimension can tell a working migration from a coincidence.
 */
@QuarkusTest
@TestProfile(EmbeddingDimensionChangeTest.OtherDimension.class)
class EmbeddingDimensionChangeTest {

    static final int DIMENSION = 8;

    @Inject
    DataSource dataSource;

    @Test
    void theColumnFollowsTheConfiguredDimension() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT atttypmod FROM pg_attribute"
                        + " WHERE attrelid = 'embeddings'::regclass AND attname = 'embedding'")) {
            result.next();
            assertEquals(DIMENSION, result.getInt(1),
                    "the store writes vectors of the configured dimension, so the column must hold them");
        }
    }

    public static class OtherDimension implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("EMBEDDING_DIMENSION", Integer.toString(DIMENSION));
        }
    }
}
