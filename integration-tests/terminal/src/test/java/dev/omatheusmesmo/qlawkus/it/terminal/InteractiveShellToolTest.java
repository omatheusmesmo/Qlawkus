package dev.omatheusmesmo.qlawkus.it.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
class InteractiveShellToolTest {

    @TestHTTPResource
    URI baseUri;

    @Test
    void startAndReadSession() throws Exception {
        HttpClient http = client();
        String command = """
                test -t 0 && test -t 1 && test -t 2 || exit 17
                printf 'ready:%s\\n' "$(stty size </dev/tty)"
                while IFS= read -r line; do
                  printf 'received:%s:%s\\n' "$line" "$(stty size)"
                done
                """;
        var start = request(http, "POST", "start?cmd=" + encode(command));
        assertEquals(200, start.statusCode(), start.body());
        String session = new ObjectMapper().readTree(start.body()).path("sessionId").asText();
        assertTrue(session.matches("[0-9a-f-]{36}"), start.body());

        try {
            awaitOutput(http, session, "ready:40 120");
            assertEquals(204, request(http, "POST", "resize/" + session + "?rows=27&columns=91").statusCode());
            String input = "test input with 'quotes'";
            var send = request(http, "POST", "send/" + session + "?input=" + encode(input));
            assertEquals(200, send.statusCode(), send.body());
            assertTrue(send.body().contains("Input sent"), send.body());
            awaitOutput(http, session, "received:" + input + ":27 91");
        } finally {
            var close = request(http, "DELETE", "close/" + session);
            assertEquals(200, close.statusCode(), close.body());
            assertTrue(close.body().contains("closed"), close.body());
        }
        assertFalse(request(http, "GET", "list").body().contains(session));
    }

    @Test
    void listSessions_returnsEmptyOrList() throws Exception {
        var response = request(client(), "GET", "list");
        assertEquals(200, response.statusCode(), response.body());
    }

    private void awaitOutput(HttpClient http, String session, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        HttpResponse<String> response;
        do {
            response = request(http, "GET", "read/" + session);
            assertEquals(200, response.statusCode(), response.body());
            if (response.body().contains(expected)) {
                return;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        fail("Expected terminal output " + expected + ", received " + response.body());
    }

    private HttpResponse<String> request(HttpClient http, String method, String path) throws Exception {
        var request = HttpRequest.newBuilder(baseUri.resolve("api/test/pty/" + path))
                .timeout(Duration.ofSeconds(5)).header("Authorization", auth())
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private static String auth() {
        String password = System.getProperty("test.qlawkus.password", "qlawkus-test");
        return "Basic " + Base64.getEncoder().encodeToString(("qlawkus:" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
