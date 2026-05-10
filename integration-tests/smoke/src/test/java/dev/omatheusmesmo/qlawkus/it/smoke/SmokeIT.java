package dev.omatheusmesmo.qlawkus.it.smoke;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.http.TestHTTPResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusIntegrationTest
class SmokeIT {

    @TestHTTPResource
    URI baseUri;

    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void nativeImage_startsAndRespondsOnApiChat() throws Exception {
        String password = System.getProperty("test.qlawkus.password", "qlawkus-it");
        String auth = Base64.getEncoder().encodeToString(("qlawkus:" + password).getBytes());

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/chat"))
                .header("Content-Type", "application/json")
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
