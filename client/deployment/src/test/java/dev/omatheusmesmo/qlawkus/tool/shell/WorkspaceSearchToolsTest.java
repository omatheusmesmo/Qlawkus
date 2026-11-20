package dev.omatheusmesmo.qlawkus.tool.shell;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tool.QlawToolProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(WorkspaceSearchToolsTest.Profile.class)
class WorkspaceSearchToolsTest {
    @Inject
    @QlawTool
    GlobTool glob;

    @Inject
    @QlawTool
    GrepTool grep;

    @Inject
    QlawToolProvider provider;

    @Test
    void toolsAreDiscoverableAndOnlyPatternIsRequired() {
        assertNotNull(glob);
        assertNotNull(grep);
        var specifications = provider.provideTools(null).tools().keySet();
        var globSpec = specifications.stream().filter(spec -> spec.name().equals("globFiles")).findFirst().orElseThrow();
        var grepSpec = specifications.stream().filter(spec -> spec.name().equals("grepFiles")).findFirst().orElseThrow();
        assertEquals(List.of("pattern"), globSpec.parameters().required());
        assertEquals(List.of("pattern"), grepSpec.parameters().required());
        assertEquals(1, ToolSpecifications.toolSpecificationsFrom(GlobTool.class).size());
        assertEquals(1, ToolSpecifications.toolSpecificationsFrom(GrepTool.class).size());
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.test-port", "0",
                    "quarkus.management.test-port", "0",
                    "qlawkus.agent.state.root", "target/search-tool-schema/state",
                    "qlawkus.agent.facts.root", "target/search-tool-schema/facts",
                    "qlawkus.agent.episodic.root", "target/search-tool-schema/journals",
                    "qlawkus.agent.working-memory.root", "target/search-tool-schema/working-memory",
                    "qlawkus.skills.roots", "target/search-tool-schema/skills");
        }
    }
}
