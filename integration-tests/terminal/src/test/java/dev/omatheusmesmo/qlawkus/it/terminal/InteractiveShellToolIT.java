package dev.omatheusmesmo.qlawkus.it.terminal;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusIntegrationTest
@DisabledOnOs(OS.WINDOWS)
class InteractiveShellToolIT extends InteractiveShellToolTest {

    @Test
    @Override
    void startAndReadSession() throws Exception {
        HttpClient http = client();

        HttpRequest startReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/pty/start?cmd=echo%20pty-native-test"))
                .header("Authorization", auth())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> startResp = http.send(startReq, HttpResponse.BodyHandlers.ofString());

        if (startResp.body().contains("native image mode")) {
            assertTrue(startResp.body().contains("not available"),
                    "Should explain PTY unavailable in native: " + startResp.body());
            return;
        }

        super.startAndReadSession();
    }
}
