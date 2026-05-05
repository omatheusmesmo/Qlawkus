package dev.omatheusmesmo.qlawkus.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.http.TestHTTPResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusIntegrationTest
@DisabledOnOs(OS.WINDOWS)
class InteractiveShellToolIT {

    @TestHTTPResource
    URI baseUri;

    private HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private String auth() {
        String password = System.getProperty("test.qlawkus.password", "qlawkus");
        return "Basic " + Base64.getEncoder().encodeToString(("qlawkus:" + password).getBytes());
    }

    @Test
    void nativeImage_startAndReadSession() throws Exception {
        HttpClient http = client();

        HttpRequest startReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/pty/start?cmd=echo%20pty-native-test"))
                .header("Authorization", auth())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> startResp = http.send(startReq, HttpResponse.BodyHandlers.ofString());

        boolean isNative = startResp.body().contains("native image mode");

        if (isNative) {
            assertTrue(startResp.body().contains("not available"), "Should explain PTY unavailable in native: " + startResp.body());
            return;
        }

        assertEquals(200, startResp.statusCode(), "startSession should return 200: " + startResp.body());
        String body = startResp.body();
        assertTrue(body.contains("sessionId"), "startSession should return sessionId: " + body);

        String sessionId = body.replaceAll(".*\"sessionId\":\"([^\"]+)\".*", "$1");

        Thread.sleep(2000);

        HttpRequest readReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/pty/read/" + sessionId))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> readResp = http.send(readReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, readResp.statusCode(), "readSession should return 200: " + readResp.body());
        assertTrue(readResp.body().toLowerCase().contains("pty-native-test") || readResp.body().contains("lines"),
                "readSession should return output: " + readResp.body());

        HttpRequest closeReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/pty/close/" + sessionId))
                .header("Authorization", auth())
                .DELETE()
                .build();
        HttpResponse<String> closeResp = http.send(closeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, closeResp.statusCode(), "closeSession should return 200: " + closeResp.body());
    }

    @Test
    void nativeImage_listSessions_returnsEmptyOrList() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/pty/list"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "listSessions should return 200: " + response.body());
    }
}
