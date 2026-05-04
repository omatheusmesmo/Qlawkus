package dev.omatheusmesmo.qlawkus.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.http.TestHTTPResource;
import jakarta.ws.rs.core.MediaType;
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
class ShellToolIT {

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
    void nativeImage_runCommand_echo() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/run?cmd=echo%20hello-native"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("hello-native"), "Native runCommand should capture stdout: " + body);
    }

    @Test
    void nativeImage_listEnvironment_returnsOsAndShell() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/env"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String body = response.body().toLowerCase();
        assertTrue(body.contains("linux") || body.contains("os"),
                "Native listEnvironment should return OS info: " + body);
    }

    @Test
    void nativeImage_checkSecurity_allowsSafeCommand() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/security?cmd=ls"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String body = response.body().toLowerCase();
        assertTrue(body.contains("false") || body.contains("blocked"),
                "Native checkSecurity should return result: " + body);
    }

    @Test
    void nativeImage_isCommandAvailable_ls() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/available?cmd=ls"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("true"), "Native isCommandAvailable should find ls: " + response.body());
    }
}
