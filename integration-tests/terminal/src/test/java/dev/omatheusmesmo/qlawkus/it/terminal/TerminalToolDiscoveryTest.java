package dev.omatheusmesmo.qlawkus.it.terminal;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
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
class TerminalToolDiscoveryTest {

    @Inject
    QlawToolProvider clawToolProvider;

    @Test
    void terminalTools_areDiscoveredAsQlawToolBeans() {
        Instance<Object> clawToolBeans = Arc.container().select(Object.class, new QlawToolLiteral());

        Set<String> toolClassNames = clawToolBeans.stream()
                .map(proxy -> ClientProxy.unwrap(proxy).getClass().getName())
                .collect(Collectors.toSet());

        assertTrue(toolClassNames.contains("dev.omatheusmesmo.qlawkus.tool.shell.ShellTool"),
                "ShellTool should be discovered via @QlawTool. Got: " + toolClassNames);
        assertTrue(toolClassNames.contains("dev.omatheusmesmo.qlawkus.tool.shell.FileTool"),
                "FileTool should be discovered via @QlawTool. Got: " + toolClassNames);
        assertTrue(toolClassNames.contains("dev.omatheusmesmo.qlawkus.tool.shell.InteractiveShellTool"),
                "InteractiveShellTool should be discovered via @QlawTool. Got: " + toolClassNames);
    }

    @Test
    void clawToolProvider_providesAllTerminalToolSpecs() {
        ToolProviderResult result = clawToolProvider.provideTools(null);

        assertNotNull(result, "QlawToolProvider should return a non-null result");
        assertFalse(result.tools().isEmpty(), "QlawToolProvider should provide tool specs");

        Set<String> toolNames = result.tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());

        assertTrue(toolNames.contains("runCommand"), "Should provide runCommand tool. Got: " + toolNames);
        assertTrue(toolNames.contains("writeFile"), "Should provide writeFile tool. Got: " + toolNames);
        assertTrue(toolNames.contains("readFile"), "Should provide readFile tool. Got: " + toolNames);
        assertTrue(toolNames.contains("listFiles"), "Should provide listFiles tool. Got: " + toolNames);
        assertTrue(toolNames.contains("deleteFile"), "Should provide deleteFile tool. Got: " + toolNames);
        assertTrue(toolNames.contains("makeDirectory"), "Should provide makeDirectory tool. Got: " + toolNames);
        assertTrue(toolNames.contains("checkSecurity"), "Should provide checkSecurity tool. Got: " + toolNames);
        assertTrue(toolNames.contains("startSession"), "Should provide startSession tool. Got: " + toolNames);
        assertTrue(toolNames.contains("sendInput"), "Should provide sendInput tool. Got: " + toolNames);
        assertTrue(toolNames.contains("readSession"), "Should provide readSession tool. Got: " + toolNames);
        assertTrue(toolNames.contains("closeSession"), "Should provide closeSession tool. Got: " + toolNames);
        assertTrue(toolNames.contains("listSessions"), "Should provide listSessions tool. Got: " + toolNames);
        assertTrue(toolNames.contains("listEnvironment"), "Should provide listEnvironment tool. Got: " + toolNames);
    }

    @SuppressWarnings("serial")
    static class QlawToolLiteral extends AnnotationLiteral<QlawTool> implements QlawTool {
    }
}
