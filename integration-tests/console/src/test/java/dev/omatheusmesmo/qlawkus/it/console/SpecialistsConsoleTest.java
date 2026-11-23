package dev.omatheusmesmo.qlawkus.it.console;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@ConnectWireMock
@TestProfile(SpecialistsConsoleTest.Profile.class)
class SpecialistsConsoleTest {

    private static final String API = "/api/admin/specialists";
    private static final String PRIMARY = "/v1/chat/completions";
    private static final String OLLAMA = "/api/chat";

    WireMock wiremock;

    @BeforeEach
    void resetModelFixtures() {
        wiremock.resetToDefaultMappings();
        wiremock.resetRequests();
    }

    @Test
    void theOwnerCanCreateEditDisableAndRunFromTheCatalog() {
        String id = id();
        given().get(API).then().statusCode(401);
        given().get("/console/specialists").then().statusCode(401);
        given().auth().preemptive().basic("other", "other-test").get(API).then().statusCode(403);
        owner().get("/console/specialists").then().statusCode(200)
                .body(containsString("id=\"specialist-editor\""))
                .body(containsString("href=\"/console/specialists\""));
        owner().get("/console/specialists.js").then().statusCode(200)
                .body(containsString("textContent"));
        owner().get(API).then().statusCode(200).body("models[0]", equalTo("FALLBACK"));
        owner().body(profile("../outside", "Review the supplied changes.", true)).post(API).then().statusCode(400);

        try {
            owner().body(profile(id, "Review the supplied changes.", true)).post(API).then().statusCode(201);
            owner().body(profile(id, "Review the supplied changes.", true)).post(API).then().statusCode(409);
            stubWorker("Review the supplied changes.", "Missing a release note.");
            owner().body(run(id)).post(API + "/runs").then().statusCode(200)
                    .body("[0].status", equalTo("COMPLETE")).body("[0].evidence", equalTo("Missing a release note."));
            wiremock.verifyThat(1, postRequestedFor(urlEqualTo(OLLAMA))
                    .withRequestBody(containing("Fixture release data")));
            owner().body(profile(id, "Review the revised checklist.", false)).put(API + "/" + id)
                    .then().statusCode(200).body("enabled", equalTo(false));
            owner().body(run(id)).post(API + "/runs").then().statusCode(409);
            wiremock.verifyThat(1, postRequestedFor(urlEqualTo(OLLAMA)));
        } finally {
            owner().delete(API + "/" + id);
        }
    }

    @Test
    void aChatTurnCanCreateItsOwnReusableSpecialist() {
        String id = id();
        String vector = "[" + "0.01,".repeat(1023) + "0.01]";
        wiremock.register(post(urlEqualTo("/v1/embeddings")).willReturn(okJson(
                "{\"data\":[{\"embedding\":" + vector + ",\"index\":0}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}")));
        wiremock.register(post(urlEqualTo(PRIMARY)).atPriority(3).willReturn(okJson("""
                {"id":"create","object":"chat.completion","created":1,"model":"fixture",
                 "choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant",
                 "tool_calls":[{"id":"catalog-create","type":"function","function":{"name":"createSpecialist",
                  "arguments":"{\\"id\\":\\"%s\\",\\"instructions\\":\\"Summarize release notes.\\"}"}}]}}]}
                """.formatted(id))));
        stubWorker("Summarize release notes.", "The fixture has three changes.");
        wiremock.register(post(urlEqualTo(PRIMARY)).atPriority(2)
                .withRequestBody(containing("Created specialist " + id)).willReturn(okJson("""
                        {"id":"run","object":"chat.completion","created":1,"model":"fixture",
                         "choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant",
                         "tool_calls":[{"id":"catalog-run","type":"function","function":{"name":"runSpecialists",
                          "arguments":"{\\"specialists\\":\\"%s\\",\\"pattern\\":\\"sequence\\",\\"snapshot\\":\\"Fixture release data\\"}"}}]}}]}
                        """.formatted(id))));
        wiremock.register(post(urlEqualTo(PRIMARY)).atPriority(1)
                .withRequestBody(containing("The fixture has three changes."))
                .willReturn(okJson("""
                        {"id":"done","object":"chat.completion","created":1,"model":"fixture",
                         "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"Specialist ready."}}]}
                        """)));
        try {
            owner().body("{\"message\":\"Create a reusable specialist to summarize release notes.\"}")
                    .post("/api/chat/sync").then().statusCode(200).body(containsString("Specialist ready."));
            wiremock.verifyThat(postRequestedFor(urlEqualTo(PRIMARY))
                    .withRequestBody(containing("Created specialist " + id)));
            wiremock.verifyThat(1, postRequestedFor(urlEqualTo(OLLAMA))
                    .withRequestBody(containing("Fixture release data")));
            owner().get(API).then().statusCode(200)
                    .body("specialists.find { it.id == '" + id + "' }.model", equalTo("FALLBACK"));
        } finally {
            owner().delete(API + "/" + id);
        }
    }

    @Test
    void sequencesShareEvidenceAndParallelWorkersReadIndependently() {
        String first = id();
        String second = id();
        try {
            owner().body(profile(first, "Identify release facts.", true)).post(API).then().statusCode(201);
            owner().body(profile(second, "Identify release questions.", true)).post(API).then().statusCode(201);
            stubWorker("Identify release facts.", "The build succeeded.");
            stubWorker("Identify release questions.", "Who approves this release?");
            owner().body(plan("SEQUENCE", first, second)).post(API + "/runs").then().statusCode(200)
                    .body("[0].status", equalTo("COMPLETE")).body("[1].status", equalTo("COMPLETE"));
            wiremock.verifyThat(1, postRequestedFor(urlEqualTo(OLLAMA))
                    .withRequestBody(containing("Identify release questions."))
                    .withRequestBody(containing("The build succeeded.")));

            wiremock.resetRequests();
            owner().body(plan("PARALLEL", first, second)).post(API + "/runs").then().statusCode(200)
                    .body("[0].specialist", equalTo(first)).body("[1].specialist", equalTo(second));
            wiremock.verifyThat(2, postRequestedFor(urlEqualTo(OLLAMA)));
            wiremock.verifyThat(0, postRequestedFor(urlEqualTo(OLLAMA))
                    .withRequestBody(containing("Earlier specialist evidence:")));
        } finally {
            owner().delete(API + "/" + first);
            owner().delete(API + "/" + second);
        }
    }

    private void stubWorker(String instructions, String answer) {
        wiremock.register(post(urlEqualTo(OLLAMA)).withRequestBody(containing(instructions)).willReturn(okJson("""
                {"model":"fixture","created_at":"2026-01-01T00:00:00Z","message":{"role":"assistant","content":"%s"},
                 "done":true,"done_reason":"stop","prompt_eval_count":20,"eval_count":5}
                """.formatted(answer))));
    }

    private static String profile(String id, String instructions, boolean enabled) {
        return """
                {"id":"%s","instructions":"%s","model":"FALLBACK","enabled":%s}
                """.formatted(id, instructions, enabled);
    }

    private static String run(String id) {
        return """
                {"pattern":"SEQUENCE","specialists":["%s"],"snapshot":"Fixture release data"}
                """.formatted(id);
    }

    private static String id() {
        return "release-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String plan(String pattern, String first, String second) {
        return """
                {"pattern":"%s","specialists":["%s","%s"],"snapshot":"Fixture release data"}
                """.formatted(pattern, first, second);
    }

    private static RequestSpecification owner() {
        return given().auth().preemptive().basic("qlawkus", "qlawkus-test").contentType("application/json");
    }

    public static class Profile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("qlawkus.agent.state.root", "target/console/specialist-state",
                    "qlawkus.agent.tool-search.enabled", "false",
                    "quarkus.security.users.embedded.users.other", "other-test",
                    "quarkus.security.users.embedded.roles.other", "admin",
                    "quarkus.langchain4j.ollama.\"fallback\".base-url",
                    "http://localhost:${quarkus.wiremock.devservices.port}");
        }
    }
}
