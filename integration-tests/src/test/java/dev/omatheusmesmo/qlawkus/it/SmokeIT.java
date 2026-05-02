package dev.omatheusmesmo.qlawkus.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.http.TestHTTPResource;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusIntegrationTest
class SmokeIT {

    @TestHTTPResource
    URI baseUri;

    @Test
    void nativeImage_startsAndRespondsOnApiChat() throws Exception {
        String auth = Base64.getEncoder().encodeToString("qlawkus:qlawkus-test".getBytes());

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/chat"))
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .header("Authorization", "Basic " + auth)
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"Say exactly: hello\"}"))
                .build();

        HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, httpResponse.statusCode(),
                "Native image SSE endpoint should return 200");
        assertFalse(httpResponse.body().isBlank(),
                "Native image SSE response should contain LLM output");
    }
}
