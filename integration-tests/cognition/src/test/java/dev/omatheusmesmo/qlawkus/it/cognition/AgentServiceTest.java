package dev.omatheusmesmo.qlawkus.it.cognition;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.cognition.Mood;
import dev.omatheusmesmo.qlawkus.store.pg.SoulEntity;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.testing.QlawkusTestUtils;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import dev.omatheusmesmo.qlawkus.testing.SoulResetHelper;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@ConnectWireMock
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentServiceTest {

    WireMock wiremock;

    @Inject
    AgentService agentService;

    @Inject
    WorkingMemoryStore memoryStore;

    @BeforeEach
    void setupStubs() {
        QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
    }

    @AfterEach
    @Transactional
    void resetSoul() {
        SoulResetHelper.resetToDefaults();
    }

    @AfterEach
    void clearMemory() {
        memoryStore.deleteMessages("default");
    }

    @Test
    @Order(1)
    void agentService_isInjectable() {
        assertNotNull(agentService);
    }

    @Test
    @Order(2)
    void chat_returnsResponseWithSoulIdentity() {
        assertEquals("Qlawkus", currentName());

        String response = agentService.chat("it-test", "What is your name? Reply with just your name, nothing else. Do not use any tools.")
                .collect()
                .in(StringBuilder::new, StringBuilder::append)
                .await()
                .atMost(Duration.ofSeconds(300))
                .toString();
        assertNotNull(response);
        assertThat(response, QlawkusTestUtils.containsStringOrMock("qlawkus"));
    }

    @Test
    @Order(3)
    void chat_usesToolToChangeOwnName() {
        String response = agentService.chat("it-test", "Change your name to Nova. Use your available tools to do it.")
                .collect()
                .in(StringBuilder::new, StringBuilder::append)
                .await()
                .atMost(Duration.ofSeconds(300))
                .toString();
        assertNotNull(response);

        QlawkusTestUtils.assertConditionOrMock(
                "Nova".equals(currentName()),
                "LLM should have used the updateName tool to change the soul name to 'Nova'. Got: " + currentName());
    }

    @Test
    @Order(4)
    void chat_usesToolToChangeMood() {
        String response = agentService.chat("it-test", "Change your mood to CURIOUS. Use your available tools to do it.")
                .collect()
                .in(StringBuilder::new, StringBuilder::append)
                .await()
                .atMost(Duration.ofSeconds(300))
                .toString();
        assertNotNull(response);

        QlawkusTestUtils.assertConditionOrMock(
                Mood.CURIOUS.equals(currentMood()),
                "LLM should have used the updateMood tool. Got: " + currentMood());
    }

    @Test
    @Order(5)
    void chat_usesToolToUpdateState() {
        String response = agentService.chat("it-test", "Update your current state to 'Testing LLM tool integration'. Use your available tools to do it.")
                .collect()
                .in(StringBuilder::new, StringBuilder::append)
                .await()
                .atMost(Duration.ofSeconds(300))
                .toString();
        assertNotNull(response);

        String state = currentState().toLowerCase();
        QlawkusTestUtils.assertConditionOrMock(
                state.contains("testing") || state.contains("llm") || state.contains("tool"),
                "LLM should have used the updateCurrentState tool. Got: " + currentState());
    }

    @Test
    @Order(6)
    void chat_usesToolToChangeCoreIdentity() {
        String response = agentService.chat("it-test", "Rewrite your core identity to include 'I am a test agent for integration testing.' Use your available tools to do it.")
                .collect()
                .in(StringBuilder::new, StringBuilder::append)
                .await()
                .atMost(Duration.ofSeconds(300))
                .toString();
        assertNotNull(response);

        String identity = currentCoreIdentity().toLowerCase();
        QlawkusTestUtils.assertConditionOrMock(
                identity.contains("test agent") || identity.contains("integration test"),
                "LLM should have used the updateCoreIdentity tool. Got identity containing: "
                        + currentCoreIdentity().substring(0, Math.min(200, currentCoreIdentity().length())));
    }

    @Transactional
    String currentName() {
        return SoulEntity.findSoul().name;
    }

    @Transactional
    Mood currentMood() {
        return SoulEntity.findSoul().mood;
    }

    @Transactional
    String currentState() {
        return SoulEntity.findSoul().currentState;
    }

    @Transactional
    String currentCoreIdentity() {
        return SoulEntity.findSoul().coreIdentity;
    }
}
