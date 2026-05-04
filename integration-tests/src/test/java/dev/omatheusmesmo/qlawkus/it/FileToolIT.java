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
class FileToolIT {

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
    void nativeImage_writeAndRead_roundTrip() throws Exception {
        HttpRequest writeReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/file/write?path=native-it-test.txt&content=hello-native"))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> writeResp = client().send(writeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, writeResp.statusCode());
        assertTrue(writeResp.body().contains("true"), "Write should succeed: " + writeResp.body());

        HttpRequest readReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/file/read?path=native-it-test.txt"))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> readResp = client().send(readReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, readResp.statusCode());
        assertTrue(readResp.body().contains("hello-native"), "Read should return written content: " + readResp.body());
    }

    @Test
    void nativeImage_listFiles_returnsEntries() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/file/list?path=."))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertFalse(resp.body().contains("ERROR"), "List should succeed: " + resp.body());
    }

    @Test
    void nativeImage_readFile_pathTraversal_blocked() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/file/read?path=../../../etc/passwd"))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("false"), "Path traversal read should be blocked: " + resp.body());
    }

    @Test
    void nativeImage_makeDirectory_succeeds() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/file/mkdir?path=native-it-mkdir-test"))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("true"), "mkdir should succeed: " + resp.body());
    }
}
