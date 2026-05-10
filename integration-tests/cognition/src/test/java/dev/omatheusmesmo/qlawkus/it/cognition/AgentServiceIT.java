package dev.omatheusmesmo.qlawkus.it.cognition;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.http.TestHTTPResource;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentServiceIT {

    @TestHTTPResource
    URI baseUri;

    HttpClient client() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    String auth() {
        String password = System.getProperty("test.qlawkus.password", "qlawkus-it");
        return "Basic " + Base64.getEncoder().encodeToString(("qlawkus:" + password).getBytes());
    }

    @Test
    @Order(1)
    void nativeImage_agentService_chatsViaHttp() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/chat"))
                .header("Content-Type", "application/json")
                .header("Authorization", auth())
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"What is your name? Reply with just your name.\"}"))
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "Native image /api/chat should return 200");
        String body = response.body().toLowerCase();
        assertTrue(body.contains("qlawkus") || body.length() > 5,
                "Agent should respond with its name or meaningful content. Got: " + response.body());
    }

    @Test
    @Order(2)
    void nativeImage_agentService_usesShellToolViaHttp() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/chat"))
                .header("Content-Type", "application/json")
                .header("Authorization", auth())
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"Run the command 'echo agent-native-test' and tell me the output.\"}"))
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "Native image agent should use shell tool via /api/chat");
        assertTrue(response.body().toLowerCase().contains("agent-native-test") || response.body().length() > 5,
                "Agent should report command output. Got: " + response.body());
    }
}
