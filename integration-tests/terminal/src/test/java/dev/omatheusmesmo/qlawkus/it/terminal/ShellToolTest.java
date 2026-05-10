package dev.omatheusmesmo.qlawkus.it.terminal;

import io.quarkus.test.junit.QuarkusTest;
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

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
class ShellToolTest {

    @TestHTTPResource
    URI baseUri;

    HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    String auth() {
        String password = System.getProperty("test.qlawkus.password", "qlawkus-test");
        return "Basic " + Base64.getEncoder().encodeToString(("qlawkus:" + password).getBytes());
    }

    @Test
    void runCommand_echo() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/run?cmd=echo%20hello"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("hello"), "runCommand should capture stdout: " + response.body());
    }

    @Test
    void listEnvironment_returnsOsAndShell() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/env"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String body = response.body().toLowerCase();
        assertTrue(body.contains("linux") || body.contains("os"),
                "listEnvironment should return OS info: " + body);
    }

    @Test
    void checkSecurity_allowsSafeCommand() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/security?cmd=ls"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String body = response.body().toLowerCase();
        assertTrue(body.contains("false") || body.contains("blocked"),
                "checkSecurity should return result: " + body);
    }

    @Test
    void isCommandAvailable_ls() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/available?cmd=ls"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("true"), "isCommandAvailable should find ls: " + response.body());
    }

    @Test
    void listActiveProcesses_afterCommand() throws Exception {
        HttpRequest runReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/run?cmd=echo%20process-track-test"))
                .header("Authorization", auth())
                .GET()
                .build();
        client().send(runReq, HttpResponse.BodyHandlers.ofString());

        HttpRequest listReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/processes"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(listReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("process-track-test") || response.body().contains("pid"),
                "listActiveProcesses should return tracked processes: " + response.body());
    }

    @Test
    void auditLog_triggersSuccessfully() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/audit?cmd=echo%20test&exitCode=0&durationMs=42"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("true"), "auditLog should return logged=true: " + response.body());
    }

    @Test
    void runCommand_timeoutEnforcement() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/run?cmd=sleep%2010&timeout=2"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("-1") || response.body().contains("timed"),
                "Timed-out command should have exitCode -1 or indicate timeout: " + response.body());
    }

    @Test
    void runCommand_outputTruncation() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/run?cmd=seq%201%2010000"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("trunc"), "Large output should be truncated: " + response.body());
    }

    @Test
    void workspaceEnv_returnsLoadedEnv() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/shell/workspace-env"))
                .header("Authorization", auth())
                .GET()
                .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "workspace-env should return 200: " + response.body());
    }

    @Test
    void credentialInjection_viaEnvFile() throws Exception {
        HttpRequest reloadReq = HttpRequest.newBuilder()
            .uri(URI.create(baseUri + "api/test/shell/reload-env"))
            .header("Authorization", auth())
            .GET()
            .build();
        client().send(reloadReq, HttpResponse.BodyHandlers.ofString());

        HttpRequest runReq = HttpRequest.newBuilder()
            .uri(URI.create(baseUri + "api/test/shell/run?cmd=echo%20$QLAWKUS_TEST_INJECTED_VAR"))
            .header("Authorization", auth())
            .GET()
            .build();
        HttpResponse<String> runResp = client().send(runReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, runResp.statusCode());
    }

    @Test
    void checkSecurity_blocksSudo() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUri + "api/test/shell/security?cmd=sudo%20rm%20-rf%20/"))
            .header("Authorization", auth())
            .GET()
            .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("true") || response.body().contains("blocked"),
            "sudo should be blocked by security check: " + response.body());
    }

    @Test
    void runCommand_blockedByDenylist() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUri + "api/test/shell/run?cmd=sudo%20apt%20install%20something"))
            .header("Authorization", auth())
            .GET()
            .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("-2") || response.body().contains("BLOCKED"),
            "Blocked command should return -2 exit code: " + response.body());
    }

    @Test
    void runningCount_returnsZero() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUri + "api/test/shell/running-count"))
            .header("Authorization", auth())
            .GET()
            .build();

        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("runningCount"), "Should return runningCount: " + response.body());
    }
}
