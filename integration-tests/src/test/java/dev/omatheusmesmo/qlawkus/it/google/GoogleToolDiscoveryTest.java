package dev.omatheusmesmo.qlawkus.it.google;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tool.ClawToolProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GoogleWireMockProfile.class)
class GoogleToolDiscoveryTest {

    @Inject
    ClawToolProvider clawToolProvider;

    @Test
    void googleTools_areDiscoveredAsClawToolBeans() {
        Instance<Object> clawToolBeans = Arc.container().select(Object.class, new ClawToolLiteral());

        Set<String> toolClassNames = clawToolBeans.stream()
                .map(proxy -> ClientProxy.unwrap(proxy).getClass().getName())
                .collect(Collectors.toSet());

        assertTrue(toolClassNames.contains("dev.omatheusmesmo.qlawkus.tools.google.calendar.CalendarTool"),
                "CalendarTool should be discovered via @ClawTool. Got: " + toolClassNames);
        assertTrue(toolClassNames.contains("dev.omatheusmesmo.qlawkus.tools.google.gmail.GmailTool"),
                "GmailTool should be discovered via @ClawTool. Got: " + toolClassNames);
        assertTrue(toolClassNames.contains("dev.omatheusmesmo.qlawkus.tools.google.drive.DriveTool"),
                "DriveTool should be discovered via @ClawTool. Got: " + toolClassNames);
        assertTrue(toolClassNames.contains("dev.omatheusmesmo.qlawkus.tools.google.sheets.SheetsTool"),
                "SheetsTool should be discovered via @ClawTool. Got: " + toolClassNames);
        assertTrue(toolClassNames.contains("dev.omatheusmesmo.qlawkus.tools.google.storage.StorageTool"),
                "StorageTool should be discovered via @ClawTool. Got: " + toolClassNames);
    }

    @Test
    void clawToolProvider_providesAllGoogleToolSpecs() {
        ToolProviderResult result = clawToolProvider.provideTools(null);

        assertNotNull(result, "ClawToolProvider should return a non-null result");
        assertFalse(result.tools().isEmpty(), "ClawToolProvider should provide tool specs");

        Set<String> toolNames = result.tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());

        assertTrue(toolNames.contains("listEvents"), "Should provide listEvents tool. Got: " + toolNames);
        assertTrue(toolNames.contains("createEvent"), "Should provide createEvent tool. Got: " + toolNames);
        assertTrue(toolNames.contains("checkAvailability"), "Should provide checkAvailability tool. Got: " + toolNames);
        assertTrue(toolNames.contains("suggestFocusTime"), "Should provide suggestFocusTime tool. Got: " + toolNames);
        assertTrue(toolNames.contains("listEmails"), "Should provide listEmails tool. Got: " + toolNames);
        assertTrue(toolNames.contains("sendEmail"), "Should provide sendEmail tool. Got: " + toolNames);
        assertTrue(toolNames.contains("searchEmails"), "Should provide searchEmails tool. Got: " + toolNames);
        assertTrue(toolNames.contains("listFiles"), "Should provide listFiles tool. Got: " + toolNames);
        assertTrue(toolNames.contains("uploadFile"), "Should provide uploadFile tool. Got: " + toolNames);
        assertTrue(toolNames.contains("downloadFile"), "Should provide downloadFile tool. Got: " + toolNames);
        assertTrue(toolNames.contains("shareFile"), "Should provide shareFile tool. Got: " + toolNames);
        assertTrue(toolNames.contains("readSheet"), "Should provide readSheet tool. Got: " + toolNames);
        assertTrue(toolNames.contains("writeSheet"), "Should provide writeSheet tool. Got: " + toolNames);
        assertTrue(toolNames.contains("updateCell"), "Should provide updateCell tool. Got: " + toolNames);
        assertTrue(toolNames.contains("listBuckets"), "Should provide listBuckets tool. Got: " + toolNames);
        assertTrue(toolNames.contains("uploadObject"), "Should provide uploadObject tool. Got: " + toolNames);
        assertTrue(toolNames.contains("downloadObject"), "Should provide downloadObject tool. Got: " + toolNames);
    }

    @SuppressWarnings("serial")
    static class ClawToolLiteral extends AnnotationLiteral<ClawTool> implements ClawTool {
    }
}
