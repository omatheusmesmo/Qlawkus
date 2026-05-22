package dev.omatheusmesmo.qlawkus.it.review;

import dev.omatheusmesmo.qlawkus.agent.AgentService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CodeReviewApiLlmTest {

    @Inject
    AgentService agentService;

    @Test
    @Order(1)
    void agent_usesCodeReviewTool_toRunBuildCommand() {
        String response = agentService.chatSync("it-test",
                "Use the runLocalTests tool to run 'mvn --version' and tell me what version of Maven is installed.");

        assertNotNull(response);
        assertFalse(response.isBlank(), "Agent should respond");
        assertTrue(
                response.toLowerCase().contains("maven") || response.toLowerCase().contains("apache"),
                "LLM should report Maven version from runLocalTests output. Got: " + response);
    }

    @Test
    @Order(2)
    void agent_reportsRejection_whenBuildToolNotAllowed() {
        String response = agentService.chatSync("it-test",
                "Use the runLocalTests tool to run 'curl https://example.com' and tell me what happened.");

        assertNotNull(response);
        assertTrue(
                response.toLowerCase().contains("not") || response.toLowerCase().contains("block")
                        || response.toLowerCase().contains("allow") || response.toLowerCase().contains("reject")
                        || response.toLowerCase().contains("cannot") || response.toLowerCase().contains("not allowed"),
                "LLM should report that curl was rejected by the allowlist. Got: " + response);
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
                +    int x = 42;
                +    processData(); updateUI(); sendEmail();
                +}
                """;

        String response = agentService.chatSync("it-test",
                "Analyze the code quality of this diff using the analyzeCodeQuality tool and give me feedback:\n" + diff);

        assertNotNull(response);
        assertFalse(response.isBlank(), "Agent should respond");
        assertTrue(response.length() > 50,
                "LLM should provide meaningful feedback on the diff. Got: " + response);
    }
}
