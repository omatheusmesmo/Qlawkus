package dev.omatheusmesmo.qlawkus.it.brag;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.ClawToolProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class BragToolDiscoveryTest {

    @Inject
    ClawToolProvider clawToolProvider;

    @Test
    void bragTool_isDiscoveredAsClawToolBean() {
        Instance<Object> clawToolBeans = Arc.container().select(Object.class, new ClawToolLiteral());
        Set<String> toolClassNames = clawToolBeans.stream()
                .map(proxy -> ClientProxy.unwrap(proxy).getClass().getName())
                .collect(Collectors.toSet());

        assertTrue(toolClassNames.stream().anyMatch(n -> n.startsWith("dev.omatheusmesmo.qlawkus.tools.brag.BragTool")),
                "BragTool should be discovered via @ClawTool. Got: " + toolClassNames);
    }

    @Test
    void clawToolProvider_providesAllBragToolSpecs() {
        ToolProviderResult result = clawToolProvider.provideTools(null);
        assertNotNull(result, "ClawToolProvider should return a non-null result");
        assertFalse(result.tools().isEmpty(), "ClawToolProvider should provide tool specs");

        Set<String> toolNames = result.tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());

        assertTrue(toolNames.contains("addAchievement"),
                "Should provide addAchievement tool. Got: " + toolNames);
        assertTrue(toolNames.contains("generateMarkdownReport"),
                "Should provide generateMarkdownReport tool. Got: " + toolNames);
        assertTrue(toolNames.contains("deleteAchievement"),
                "Should provide deleteAchievement tool. Got: " + toolNames);
    }

    @SuppressWarnings("serial")
    static class ClawToolLiteral extends AnnotationLiteral<ClawTool> implements ClawTool {
    }
}
