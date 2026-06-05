package dev.omatheusmesmo.qlawkus.it.review;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.testing.QlawkusTestUtils;
import dev.omatheusmesmo.qlawkus.testing.QlawkusWireMockStubs;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@ConnectWireMock
@DisabledOnOs(OS.WINDOWS)
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CodeReviewApiLlmTest {

    WireMock wiremock;

    @Inject
    AgentService agentService;

    @BeforeEach
    void setupStubs() {
        QlawkusWireMockStubs.registerOpenAiStubs(wiremock);
    }

    @Test
    @Order(1)
    void agent_usesCodeReviewTool_toRunBuildCommand() {
        String response = agentService.chatSync("it-test",
                "Use the runLocalTests tool to run 'mvn --version' and tell me what version of Maven is installed.");

        assertNotNull(response);
        assertThat(response, QlawkusTestUtils.containsStringOrMock("maven", "apache"));
    }

    @Test
    @Order(2)
    void agent_reportsRejection_whenBuildToolNotAllowed() {
        String response = agentService.chatSync("it-test",
                "Use the runLocalTests tool to run 'curl https://example.com' and tell me what happened.");

        assertNotNull(response);
        assertThat(response, QlawkusTestUtils.containsStringOrMock("not", "block", "allow", "reject", "cannot"));
    }

    @Test
    @Order(3)
    void agent_usesCodeQualityTool_toAnalyzeDiff() {
        String diff = """
                diff --git a/Foo.java b/Foo.java
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,3 +1,5 @@
                +public void doEverythingAndAlsoMore() {
                + int x = 42;
                + processData(); updateUI(); sendEmail();
                +}
                """;

        String response = agentService.chatSync("it-test",
                "Analyze the code quality of this diff using the analyzeCodeQuality tool and give me feedback:\n" + diff);

        assertNotNull(response);
        assertFalse(response.isBlank(), "Agent should respond");
        if (QlawkusTestUtils.usesLLM()) {
            assertTrue(response.length() > 50,
                    "LLM should provide meaningful feedback on the diff. Got: " + response);
        }
    }
}
