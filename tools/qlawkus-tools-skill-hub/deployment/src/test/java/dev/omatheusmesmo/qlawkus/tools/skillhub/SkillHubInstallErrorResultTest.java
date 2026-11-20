package dev.omatheusmesmo.qlawkus.tools.skillhub;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Runs {@link SkillHubTool#installSkill} through langchain4j's {@link DefaultToolExecutor}, the same
 * path the agent takes, to pin what the model and the metrics see on a failed install: an error
 * result carrying the reason. The source is a bare host, which is refused before any network call.
 */
@QuarkusTest
class SkillHubInstallErrorResultTest {

    @Inject
    @QlawTool
    SkillHubTool tool;

    @Test
    void aFailedInstallIsAnErrorResultWithTheReason() throws NoSuchMethodException {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("installSkill")
                .arguments("{\"source\":\"code.deepline.com\",\"confirm\":true}")
                .build();

        ToolExecutionResult result = new DefaultToolExecutor(tool,
                SkillHubTool.class.getMethod("installSkill", String.class, boolean.class))
                .executeWithContext(request, InvocationContext.builder().build());

        assertTrue(result.isError(), "a failed install must count as outcome=error");
        assertTrue(result.resultText().contains("code.deepline.com"), result.resultText());
    }
}
