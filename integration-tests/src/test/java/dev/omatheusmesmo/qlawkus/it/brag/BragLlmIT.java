package dev.omatheusmesmo.qlawkus.it.brag;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.ClawToolProvider;
import dev.omatheusmesmo.qlawkus.tools.brag.BragEntry;
import dev.omatheusmesmo.qlawkus.tools.brag.BragTool;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(BragTestProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
@Tag("llm-it")
class BragLlmIT {

    private static final Logger log = LoggerFactory.getLogger(BragLlmIT.class);

    @Inject
    AgentService agentService;

    @Inject
    ClawToolProvider clawToolProvider;

    @BeforeEach
    void rateLimitPause() throws InterruptedException {
        Thread.sleep(30_000);
    }

    @AfterEach
    @Transactional
    void cleanup() {
        BragEntry.deleteAll();
    }

    @Test
    @Order(1)
    void bragTool_isDiscoveredByClawToolProvider() {
        @SuppressWarnings("serial")
        var literal = new AnnotationLiteral<ClawTool>() {};
        Instance<Object> clawToolBeans = Arc.container().select(Object.class, literal);

        Set<String> toolClassNames = clawToolBeans.stream()
            .map(proxy -> ClientProxy.unwrap(proxy).getClass().getName())
            .collect(Collectors.toSet());

        log.info("Discovered @ClawTool beans: {}", toolClassNames);
assertTrue(toolClassNames.stream().anyMatch(n -> n.startsWith("dev.omatheusmesmo.qlawkus.tools.brag.BragTool")),
                "BragTool should be discovered via @ClawTool. Got: " + toolClassNames);

        ToolProviderResult result = clawToolProvider.provideTools(null);
        Set<String> toolNames = result.tools().keySet().stream()
            .map(ToolSpecification::name)
            .collect(Collectors.toSet());

        log.info("ClawToolProvider provides tools: {}", toolNames);
        assertTrue(toolNames.contains("addAchievement"),
            "ClawToolProvider should provide addAchievement. Got: " + toolNames);
    }

    @Test
    @Order(2)
    void agent_addsBragEntry_whenAskedToAddAchievement() {
        long countBefore = QuarkusTransaction.requiringNew().call(() -> BragEntry.count());

        String response = agentService.chat(
                "I changed the database index on the users table to improve query performance. "
                + "You have a tool called addAchievement - use it NOW to record this achievement. "
                + "Call addAchievement with achievement='Changed database index on users table for performance', "
                + "date='today', repo='unknown'.")
            .collect()
            .in(StringBuilder::new, StringBuilder::append)
            .await()
            .atMost(Duration.ofSeconds(300))
            .toString();

        log.info("Agent response: {}", response);

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

        Boolean hasImpact = QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            var entries = BragEntry.listAll();
            if (entries.isEmpty()) return false;
            BragEntry entry = (BragEntry) entries.get(0);
            return entry.impact != null;
        });
        assertTrue(hasImpact, "Impact should be translated by LLM");
    }
}
