package dev.omatheusmesmo.qlawkus.devui;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the JSON-RPC methods the live pages call. Only the read side is covered: the job
 * triggers delegate to LLM-backed jobs, and their behavior is already pinned where those jobs are
 * tested - here they would only prove that a network call can be started.
 */
class QlawkusDevUIJsonRPCTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest().withEmptyApplication();

    QlawkusDevUIJsonRPCTest() {
        super("qlawkus-client");
    }

    @Test
    void soul_reportsThePersonaFields() throws Exception {
        JsonNode soul = super.executeJsonRPCMethod("getSoul");

        assertNotNull(soul);
        assertTrue(soul.has("name"));
        assertTrue(soul.has("coreIdentity"));
        assertTrue(soul.has("currentState"));
        assertTrue(soul.has("mood"));
    }

    @Test
    void userProfile_reportsTheOwnerBlock() throws Exception {
        JsonNode profile = super.executeJsonRPCMethod("getUserProfile");

        assertNotNull(profile);
        assertTrue(profile.has("name"));
        assertTrue(profile.has("profile"));
    }

    @Test
    void memorySummary_reportsTheSameShapeAsTheAdminApi() throws Exception {
        JsonNode summary = super.executeJsonRPCMethod("getMemorySummary");

        assertNotNull(summary);
        assertTrue(summary.get("embeddingSources").isArray());
        assertTrue(summary.get("journalCount").isNumber());
        assertTrue(summary.get("chatMessageCount").isNumber());
    }

    @Test
    void facts_honourTheRequestedLimit() throws Exception {
        JsonNode facts = super.executeJsonRPCMethod("getFacts", Map.of("limit", 5));

        assertNotNull(facts);
        assertTrue(facts.isArray());
        assertTrue(facts.size() <= 5);
    }

    @Test
    void skills_returnTheInjectedIndexShape() throws Exception {
        JsonNode skills = super.executeJsonRPCMethod("getSkills");

        assertNotNull(skills);
        assertTrue(skills.isArray());
    }

    @Test
    void registeredTools_listTheLiveContainerBeans() throws Exception {
        JsonNode tools = super.executeJsonRPCMethod("getRegisteredTools");

        assertNotNull(tools);
        assertTrue(tools.isArray());
        assertFalse(tools.isEmpty(), "the skeleton always registers @QlawTool beans");
        assertTrue(tools.get(0).hasNonNull("className"));
        assertTrue(tools.get(0).hasNonNull("simpleName"));
    }
}
