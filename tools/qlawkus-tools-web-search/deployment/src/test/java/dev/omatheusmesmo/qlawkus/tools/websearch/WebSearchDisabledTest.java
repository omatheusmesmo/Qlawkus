package dev.omatheusmesmo.qlawkus.tools.websearch;

import static org.junit.jupiter.api.Assertions.*;

import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The extension can be shipped without provisioning any search service or credential. */
@QuarkusTest
@TestProfile(WebSearchDisabledTest.Profile.class)
class WebSearchDisabledTest {
    @Inject @QlawTool WebSearchTool tool;
    @Inject WebSearchConfig config;

    @Test
    void bootsWithNoEndpointAndSearchDisabled() {
        assertTrue(config.baseUrl().isEmpty());
        assertTrue(tool.searchWeb("test").contains("enabled=false"));
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("qlawkus.web-search.enabled", "false");
        }
    }
}
