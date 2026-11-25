package dev.omatheusmesmo.qlawkus.it.image;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.store.pg.CognitionBackendState;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tools.brag.BragCleanupJob;
import dev.omatheusmesmo.qlawkus.tools.brag.BragTool;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleOAuthState;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleOAuthStateStore;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The published image carries the Postgres backend and every tool that owns a database, and is given no
 * JDBC URL for any of them. It must boot on files with all of those databases switched off.
 */
@QuarkusTest
class ImageWithoutDatabaseTest {

    @Inject
    AgentService agentService;

    @Inject
    CognitionBackendState backend;

    @Inject
    @QlawTool
    BragTool bragTool;

    @Inject
    BragCleanupJob cleanupJob;

    @Inject
    GoogleOAuthStateStore oauthStates;

    @Test
    void bootsOnFilesWithEveryDatabaseSwitchedOff() {
        assertNotNull(agentService);
        assertFalse(backend.usesPostgres(), "no URL was given, so the cognition stores are markdown");
        for (String name : List.of("brag", "google-auth")) {
            assertFalse(datasourceIsActive(name), "the " + name + " database must stay off without a URL");
        }
    }

    @Test
    void readinessIsUpWithNoDatabase() {
        given().when().get("/q/health/ready").then().statusCode(200);
    }

    @Test
    void theBragToolSaysItNeedsADatabaseInsteadOfFailing() {
        String reply = bragTool.addAchievement("Shipped the zero-config quickstart", null, null);

        assertTrue(reply.contains("database"), reply);
        assertTrue(bragTool.deleteAchievement(1).contains("database"));
    }

    @Test
    void theBragCleanupJobSkipsQuietlyWithoutADatabase() {
        cleanupJob.purgeExpiredEntries();
    }

    @Test
    void googleSignInStateLivesInMemoryWithoutADatabase() {
        String token = oauthStates.issue("memory-1", "discord", "chat-1");

        GoogleOAuthState state = oauthStates.consume(token);

        assertEquals("memory-1", state.memoryId);
        assertEquals("chat-1", state.chatId);
        assertNull(oauthStates.consume(token), "a state token works once");
    }

    private static boolean datasourceIsActive(String name) {
        return Arc.container().instance(AgroalDataSource.class, new DataSource.DataSourceLiteral(name))
                .getBean().isActive();
    }
}
