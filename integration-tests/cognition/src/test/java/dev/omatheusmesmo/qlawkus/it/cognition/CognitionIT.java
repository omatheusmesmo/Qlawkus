package dev.omatheusmesmo.qlawkus.it.cognition;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.http.TestHTTPResource;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusIntegrationTest
class CognitionIT {

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
    void nativeImage_apiChatEndpoint_respondsWithLLM() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/chat"))
                .header("Content-Type", "application/json")
                .header("Authorization", auth())
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"Say exactly: cognition-native-ok\"}"))
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                "Native image /api/chat should return 200");
        assertFalse(response.body().isBlank(),
                "Native image /api/chat should return LLM output");
    }
}
