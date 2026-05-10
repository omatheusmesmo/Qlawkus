package dev.omatheusmesmo.qlawkus.it.terminal;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.http.TestHTTPResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@DisabledOnOs(OS.WINDOWS)
class FileToolTest {

    @TestHTTPResource
    URI baseUri;

    @AfterEach
    void cleanup() throws IOException {
        deleteIfExists("jvm-it-test.txt");
        deleteIfExists("delete-test.txt");
        deleteDirIfExists("jvm-it-mkdir-test");
    }

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
    void writeAndRead_roundTrip() throws Exception {
        HttpRequest writeReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/file/write?path=jvm-it-test.txt&content=hello-jvm"))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> writeResp = client().send(writeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, writeResp.statusCode());
        assertTrue(writeResp.body().contains("true"), "Write should succeed: " + writeResp.body());

        HttpRequest readReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/file/read?path=jvm-it-test.txt"))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> readResp = client().send(readReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, readResp.statusCode());
        assertTrue(readResp.body().contains("hello-jvm"), "Read should return written content: " + readResp.body());
    }

    @Test
    void listFiles_returnsEntries() throws Exception {
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
    void readFile_pathTraversal_blocked() throws Exception {
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
    void makeDirectory_succeeds() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/file/mkdir?path=jvm-it-mkdir-test"))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("true"), "mkdir should succeed: " + resp.body());
    }

    @Test
    void deleteFile_succeeds() throws Exception {
        HttpRequest writeReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/file/write?path=delete-test.txt&content=to-delete"))
                .header("Authorization", auth())
                .GET()
                .build();
        client().send(writeReq, HttpResponse.BodyHandlers.ofString());

        HttpRequest deleteReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/file/delete?path=delete-test.txt"))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> deleteResp = client().send(deleteReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleteResp.statusCode());
        assertTrue(deleteResp.body().contains("true"), "Delete should succeed: " + deleteResp.body());
    }

    @Test
    void writeFile_pathTraversal_blocked() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/test/file/write?path=../../../tmp/evil.txt&content=pwned"))
                .header("Authorization", auth())
                .GET()
                .build();
        HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("false"), "Path traversal write should be blocked: " + resp.body());
    }

    private void deleteIfExists(String name) throws IOException {
        Files.deleteIfExists(Path.of(name));
    }

    private void deleteDirIfExists(String name) throws IOException {
        Path dir = Path.of(name);
        if (Files.exists(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    Files.deleteIfExists(p);
                }
            }
            Files.deleteIfExists(dir);
        }
    }
}
