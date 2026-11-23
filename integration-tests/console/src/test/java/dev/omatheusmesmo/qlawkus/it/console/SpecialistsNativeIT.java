package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@QuarkusTestResource(value = SpecialistsNativeIT.WorkerFixture.class, restrictToAnnotatedClass = true)
class SpecialistsNativeIT {

    private static final String API = "/api/admin/specialists";

    @Test
    void packagesValidationPersistenceAndSpecialistExecution() {
        String definition = """
                {"id":"native-review","instructions":"Review the supplied data.","model":"FALLBACK","enabled":true}
                """;
        given().get(API).then().statusCode(401);
        owner().get("/console/specialists").then().statusCode(200);
        owner().body(definition.replace("native-review", "../invalid")).post(API).then().statusCode(400);
        owner().body(definition).post(API).then().statusCode(201);
        owner().body("""
                {"pattern":"SEQUENCE","specialists":["native-review"],"snapshot":"native fixture"}
                """).post(API + "/runs").then().statusCode(200)
                .body("[0].status", equalTo("COMPLETE")).body("[0].evidence", equalTo("Native evidence."));
        owner().get(API).then().statusCode(200).body("specialists[0].id", equalTo("native-review"));
        owner().delete(API + "/native-review").then().statusCode(204);
    }

    private static RequestSpecification owner() {
        return given().auth().preemptive().basic("qlawkus", "qlawkus-test").contentType("application/json");
    }

    public static class WorkerFixture implements QuarkusTestResourceLifecycleManager {

        private HttpServer server;

        @Override
        public Map<String, String> start() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext("/api/chat", exchange -> {
                    try (exchange) {
                        exchange.getRequestBody().readAllBytes();
                        byte[] body = """
                                {"model":"fixture","created_at":"2026-01-01T00:00:00Z",
                                 "message":{"role":"assistant","content":"Native evidence."},
                                 "done":true,"done_reason":"stop","prompt_eval_count":20,"eval_count":5}
                                """.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, body.length);
                        exchange.getResponseBody().write(body);
                    }
                });
                server.start();
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot start the specialist model fixture", failure);
            }
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            Path root = Path.of("target", "specialists-native", UUID.randomUUID().toString()).toAbsolutePath();
            return Map.of("qlawkus.agent.state.root", root.resolve("state").toString(),
                    "qlawkus.agent.working-memory.root", root.resolve("working-memory").toString(),
                    "qlawkus.agent.facts.root", root.resolve("facts").toString(),
                    "qlawkus.agent.episodic.root", root.resolve("journal").toString(),
                    "qlawkus.workspace.root", root.resolve("workspace").toString(),
                    "qlawkus.skills.roots", root.resolve("skills").toString(),
                    "qlawkus.test.mock-mode", "true",
                    "qlawkus.startup-thought.enabled", "false",
                    "quarkus.langchain4j.ollama.\"fallback\".base-url", url,
                    "quarkus.langchain4j.openai.\"primary\".api-key", "fixture-key");
        }

        @Override
        public void stop() {
            if (server != null) {
                server.stop(0);
            }
        }
    }
}
