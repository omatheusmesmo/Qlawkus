package dev.omatheusmesmo.qlawkus.tools.webfetch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(WebFetchDisabledTest.Profile.class)
class WebFetchDisabledTest {

    @Inject
    @QlawTool
    WebFetchTool tool;

    @Test
    void aDisabledToolSaysSoAndSendsNothing() {
        String output = tool.fetchUrl("http://127.0.0.1:1/never", null);
        assertTrue(output.contains("qlawkus.web-fetch.enabled=false"), output);
        assertFalse(output.contains("<web-fetch-data"), output);
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("qlawkus.web-fetch.enabled", "false");
        }
    }
}
