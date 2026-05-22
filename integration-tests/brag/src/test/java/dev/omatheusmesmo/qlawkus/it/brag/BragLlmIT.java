package dev.omatheusmesmo.qlawkus.it.brag;

import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.tools.brag.BragEntry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BragLlmIT {

    @Inject
    AgentService agentService;

    @BeforeEach
    void rateLimitPause() {
    }

    @AfterEach
    @Transactional
    void cleanup() {
        BragEntry.deleteAll();
    }

    @Test
    @Order(1)
    void agent_addsBragEntry_whenAskedToAddAchievement() {
        long countBefore = QuarkusTransaction.requiringNew().call(() -> BragEntry.count());

        String response = agentService.chatSync("it-test",
                "I changed the database index on the users table to improve query performance. "
                        + "Use the addAchievement tool to record this. "
                        + "Call addAchievement with achievement='Changed database index on users table for performance', "
                        + "date='today', repo='unknown'.");

        assertTrue(response != null && !response.isBlank(),
                "LLM should return a non-blank response. Got: '" + response + "'");

        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    long countAfter = QuarkusTransaction.requiringNew().call(() -> BragEntry.count());
                    assertTrue(countAfter > countBefore,
                            "Expected at least one new BragEntry after agent chat. Response was: " + response);

                    Boolean found = QuarkusTransaction.requiringNew().call(() -> {
                        @SuppressWarnings("unchecked")
                        var entries = BragEntry.listAll();
                        return entries.stream().anyMatch(e -> {
                            BragEntry entry = (BragEntry) e;
                            String achievement = entry.achievement.toLowerCase();
                            return (achievement.contains("index") || achievement.contains("database"))
                                    && !entry.deleted;
                        });
                    });
                    assertTrue(found, "Expected achievement mentioning 'index' or 'database'");
                });
    }

    @Test
    @Order(2)
    void agent_translatesImpact_whenRecordingAchievement() {
        String response = agentService.chatSync("it-test",
                "Record that I optimized the caching layer. Use the addAchievement tool with "
                        + "achievement='Optimized caching layer', date='today', repo='api-service'.");

        assertTrue(response != null && !response.isBlank(),
                "LLM should return a non-blank response. Got: '" + response + "'");

        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    Boolean hasImpact = QuarkusTransaction.requiringNew().call(() -> {
                        @SuppressWarnings("unchecked")
                        var entries = BragEntry.listAll();
                        if (entries.isEmpty()) return false;
                        BragEntry entry = (BragEntry) entries.get(0);
                        return entry.impact != null && !entry.impact.isBlank();
                    });
                    assertTrue(hasImpact, "Impact should be translated by LLM");
                });
    }
}
