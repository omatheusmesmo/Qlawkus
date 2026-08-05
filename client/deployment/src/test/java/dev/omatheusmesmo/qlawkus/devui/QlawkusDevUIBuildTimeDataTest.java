package dev.omatheusmesmo.qlawkus.devui;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.devui.tests.DevUIBuildTimeDataTest;
import io.quarkus.test.QuarkusDevModeTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the build-time data the Dev UI pages read. The tool list and the composition verdict are
 * computed during augmentation, so getting them wrong fails silently at runtime - the page just
 * renders an empty or misleading graph. Asserting the shape here catches that at build time.
 */
class QlawkusDevUIBuildTimeDataTest extends DevUIBuildTimeDataTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest().withEmptyApplication();

    QlawkusDevUIBuildTimeDataTest() {
        super("qlawkus-client");
    }

    @Test
    void exposesTheScannedTools() throws Exception {
        JsonNode tools = super.getBuildTimeData("qlawkusTools");

        assertNotNull(tools, "qlawkusTools must be published as build-time data");
        assertTrue(tools.isArray());
        assertFalse(tools.isEmpty(), "the skeleton alone contributes @QlawTool beans");

        JsonNode first = tools.get(0);
        assertTrue(first.hasNonNull("className"));
        assertTrue(first.hasNonNull("simpleName"));
        assertTrue(first.hasNonNull("capability"));
    }

    @Test
    void attributesSkeletonToolsToTheClient() throws Exception {
        JsonNode tools = super.getBuildTimeData("qlawkusTools");

        boolean sawShellTool = false;
        for (JsonNode tool : tools) {
            if ("ShellTool".equals(tool.get("simpleName").asText())) {
                sawShellTool = true;
                assertEquals("qlawkus-client", tool.get("capability").asText(),
                        "a skeleton tool belongs to no optional capability");
            }
        }
        assertTrue(sawShellTool, "ShellTool ships with the skeleton and must be listed");
    }

    @Test
    void exposesTheCompositionVerdict() throws Exception {
        JsonNode composition = super.getBuildTimeData("qlawkusComposition");

        assertNotNull(composition, "qlawkusComposition must be published as build-time data");
        assertTrue(composition.hasNonNull("manifestFound"));
        assertTrue(composition.hasNonNull("defaultPosture"));
        assertTrue(composition.get("except").isArray());
        assertTrue(composition.get("capabilities").isObject());
    }

    @Test
    void reportsEveryCapabilityWithBothVerdicts() throws Exception {
        JsonNode capabilities = super.getBuildTimeData("qlawkusComposition").get("capabilities");

        capabilities.fields().forEachRemaining(entry -> {
            JsonNode state = entry.getValue();
            assertTrue(state.hasNonNull("selected"),
                    entry.getKey() + " must report whether the manifest selected it");
            assertTrue(state.hasNonNull("present"),
                    entry.getKey() + " must report whether it reached the classpath");
        });
    }
}
