package dev.omatheusmesmo.qlawkus.it.console;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.artifact.ArtifactDelivery;
import dev.omatheusmesmo.qlawkus.artifact.ArtifactLibrary;
import dev.omatheusmesmo.qlawkus.artifacts.ArtifactTool;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tool.shell.WorkspaceConfinement;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@QuarkusTest
@TestProfile(ArtifactsConsoleTest.Profile.class)
@ConnectWireMock
class ArtifactsConsoleTest {

    private static final Path STATE = Path.of("target/console/artifact-test-state");
    private static final String FILE = "résumé.csv";
    private static final String REPORT = "name,value\nreport,<script>alert(1)</script>\n";
    private static final String CHAT = "/v1/chat/completions";

    WireMock wiremock;

    @Inject
    @QlawTool
    ArtifactTool tool;

    @Inject
    AgentDeliveryContext context;

    @Inject
    WorkspaceConfinement workspace;

    @Inject
    Delivery delivery;

    @TempDir
    Path outside;

    private Path fixtures;
    private String registeredId;

    @BeforeEach
    void prepare() throws Exception {
        fixtures = Files.createTempDirectory(workspace.getWorkspacePath(), "artifact-test-");
        delivery.reset();
        wiremock.resetToDefaultMappings();
        wiremock.resetRequests();
    }

    @AfterEach
    void cleanup() throws Exception {
        context.clear();
        if (registeredId != null) {
            Files.deleteIfExists(STATE.resolve("artifacts").resolve(registeredId + ".artifact"));
        }
        try (var entries = Files.newDirectoryStream(fixtures)) {
            for (Path entry : entries) {
                Files.deleteIfExists(entry);
            }
        }
        Files.deleteIfExists(fixtures);
    }

    @Test
    void consoleOnlyShowsTheOwnersReportAndServesAnInertPreview() throws Exception {
        Path report = Files.writeString(fixtures.resolve(FILE), REPORT);
        context.restore(new AgentDeliveryContext.Snapshot("console", "rest", null, "qlawkus"));
        String path = registerReport(report);
        Files.writeString(report, "changed after registration");

        given().get(path).then().statusCode(401);
        given().get("/console/artifacts").then().statusCode(401);
        anotherUser().get(path).then().statusCode(404);
        anotherUser().get(path + "/preview").then().statusCode(404);
        anotherUser().get("/console/artifacts").then().statusCode(200).body(not(containsString(FILE)));

        owner().get("/console/artifacts").then().statusCode(200)
                .body(containsString("href=\"/console/artifacts\""))
                .body(containsString("href=\"" + path + "/preview\"")).body(containsString(FILE));
        byte[] download = owner().get(path).then().statusCode(200)
                .contentType("text/csv").header("Cache-Control", "private, no-store")
                .header("Content-Disposition", "attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.csv")
                .extract().asByteArray();
        assertArrayEquals(REPORT.getBytes(StandardCharsets.UTF_8), download);
        byte[] preview = owner().get(path + "/preview").then().statusCode(200)
                .contentType("text/plain").header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .extract().asByteArray();
        assertArrayEquals(download, preview);
        owner().get("/api/artifacts/anything-else").then().statusCode(404);
    }

    @Test
    void anAuthenticatedChatTurnCanRegisterTheReportThroughTheModelToolLoop() throws Exception {
        Path report = Files.writeString(fixtures.resolve("generated.csv"), REPORT);
        String relative = workspace.getWorkspacePath().relativize(report).toString();
        String vector = "[" + "0.01,".repeat(1023) + "0.01]";
        wiremock.register(post(urlEqualTo("/v1/embeddings")).willReturn(okJson(
                "{\"data\":[{\"embedding\":" + vector + ",\"index\":0}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}")));
        wiremock.register(post(urlEqualTo(CHAT)).atPriority(2).willReturn(okJson("""
                {"id":"tool-turn","object":"chat.completion","created":1,"model":"test",
                 "choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant",
                 "tool_calls":[{"id":"save-report","type":"function","function":{"name":"registerArtifact",
                 "arguments":"{\\"path\\":\\"%s\\"}"}}]}}]}
                """.formatted(relative))));
        wiremock.register(post(urlEqualTo(CHAT)).atPriority(1)
                .withRequestBody(matchingJsonPath("$.messages[?(@.role == 'tool')]"))
                .willReturn(okJson("""
                        {"id":"final-turn","object":"chat.completion","created":1,"model":"test",
                         "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"Report ready."}}]}
                        """)));

        owner().contentType("application/json").body("{\"message\":\"Register a generated report.\"}")
                .post("/api/chat/sync").then().statusCode(200).body(containsString("Report ready."));

        wiremock.verifyThat(postRequestedFor(urlEqualTo(CHAT))
                .withRequestBody(matchingJsonPath("$.tools[?(@.function.name == 'registerArtifact')]")));
        var response = owner().get("/console/artifacts").then().statusCode(200)
                .body(containsString("generated.csv")).extract().asString();
        var link = Pattern.compile("/api/artifacts/([0-9a-f-]{36})").matcher(response);
        assertTrue(link.find());
        registeredId = link.group(1);
        wiremock.verifyThat(postRequestedFor(urlEqualTo(CHAT))
                .withRequestBody(containing("Artifact " + registeredId + " saved.")));
    }

    @Test
    void refusesTraversalSymlinksSecretsAndFilesBeyondTheBudget() throws Exception {
        context.restore(new AgentDeliveryContext.Snapshot("console", "rest", null, "qlawkus"));
        Path secret = Files.writeString(outside.resolve("outside.txt"), "outside workspace");
        Files.createSymbolicLink(fixtures.resolve("link.txt"), secret);
        Files.createSymbolicLink(fixtures.resolve("linked-dir"), outside);
        assertTrue(tool.registerArtifact("../outside.txt").contains("inside the workspace"));
        assertTrue(tool.registerArtifact(secret.toString()).contains("inside the workspace"));
        assertTrue(tool.registerArtifact(fixtures.resolve("link.txt").toString()).contains("regular workspace file"));
        assertTrue(tool.registerArtifact(fixtures.resolve("linked-dir/outside.txt").toString()).contains("could not be read"));
        assertTrue(tool.registerArtifact(Files.writeString(fixtures.resolve(".env.txt"), "x").toString()).contains("Hidden"));
        assertTrue(tool.registerArtifact("artifact-test-env.txt").contains("environment files"));
        assertTrue(tool.registerArtifact(Files.writeString(fixtures.resolve("sample.txt"),
                "-----BEGIN PRIVATE KEY-----").toString()).contains("credential"));
        Path large = Files.write(fixtures.resolve("large.csv"), new byte[2 * 1024 * 1024 + 1]);
        assertTrue(tool.registerArtifact(large.toString()).contains("2 MiB"));

        context.restore(new AgentDeliveryContext.Snapshot("console", "rest", null, "other"));
        assertTrue(tool.registerArtifact(large.toString()).contains("authenticated owner conversation"));
    }

    @Test
    void deliversOnlyToTheBoundChatAndPropagatesProviderFailure() throws Exception {
        Path report = Files.writeString(fixtures.resolve("report.txt"), REPORT);
        context.set("memory", "telegram", "123");
        String path = registerReport(report);
        assertNull(delivery.last());

        context.set("memory", "discord", "123");
        assertTrue(tool.sendArtifact(registeredId).contains("unavailable in this conversation"));
        context.set("memory", "telegram", "456");
        assertTrue(tool.sendArtifact(registeredId).contains("unavailable in this conversation"));
        assertNull(delivery.last());

        context.set("memory", "telegram", "123");
        delivery.fail(true);
        assertTrue(tool.sendArtifact(registeredId).contains("could not be delivered"));
        owner().get(path).then().statusCode(200);
        delivery.fail(false);
        assertEquals("File delivered: report.txt.", tool.sendArtifact(registeredId));
        assertEquals("telegram", delivery.last().provider());
        assertEquals("123", delivery.last().chat());
        assertArrayEquals(REPORT.getBytes(StandardCharsets.UTF_8), delivery.last().content().bytes());
    }

    private String registerReport(Path report) {
        String result = tool.registerArtifact(workspace.getWorkspacePath().relativize(report).toString());
        assertTrue(result.startsWith("Artifact "), result);
        registeredId = result.substring("Artifact ".length(), "Artifact ".length() + 36);
        return "/api/artifacts/" + UUID.fromString(registeredId);
    }

    private static RequestSpecification owner() {
        return given().auth().preemptive().basic("qlawkus", "qlawkus-test");
    }

    private static RequestSpecification anotherUser() {
        return given().auth().preemptive().basic("other", "other-test");
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("qlawkus.agent.state.root", STATE.toString(),
                    "qlawkus.workspace.root", "target/console/artifact-test-workspace",
                    "qlawkus.workspace.env-file", "artifact-test-env.txt",
                    "qlawkus.agent.tool-search.enabled", "false",
                    "quarkus.security.users.embedded.users.other", "other-test",
                    "quarkus.security.users.embedded.roles.other", "admin");
        }
    }

    @Mock
    @ApplicationScoped
    static class Delivery implements ArtifactDelivery {
        private boolean fail;
        private Sent last;

        @Override
        public Uni<Void> send(String providerId, String chatId, ArtifactLibrary.Content file) {
            last = new Sent(providerId, chatId, file);
            return fail ? Uni.createFrom().failure(new IllegalStateException("Provider refused the upload"))
                    : Uni.createFrom().voidItem();
        }

        void reset() {
            fail = false;
            last = null;
        }

        void fail(boolean fail) {
            this.fail = fail;
        }

        Sent last() {
            return last;
        }
    }

    record Sent(String provider, String chat, ArtifactLibrary.Content content) {
    }
}
