package dev.omatheusmesmo.qlawkus.messaging;

import com.sun.net.httpserver.HttpServer;
import dev.omatheusmesmo.qlawkus.attachment.AttachmentRejected;
import dev.omatheusmesmo.qlawkus.attachment.ChatAttachment;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaDownloaderTest {

    @Test
    void download_returnsBytesOnSuccess() throws Exception {
        byte[] payload = {1, 2, 3, 4, 5};
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/file", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            try (var out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            MediaDownloader downloader = new MediaDownloader(jdkClient());
            byte[] result = downloader.download("http://localhost:" + port + "/file");
            assertArrayEquals(payload, result);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void attachmentDownloadRefusesRedirectsAndCapsChunkedBodies() throws Exception {
        var redirected = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/destination");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/destination", exchange -> {
            redirected.set(true);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/oversize", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(new byte[ChatAttachment.MAX_BYTES + 1]);
            }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var downloader = new MediaDownloader(jdkClient());

            assertThrows(AttachmentRejected.class, () -> downloader.downloadAttachment(URI.create(base + "/redirect")));
            assertFalse(redirected.get());
            assertThrows(AttachmentRejected.class, () -> downloader.downloadAttachment(URI.create(base + "/oversize")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void download_throwsOnNon200() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/file", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            MediaDownloader downloader = new MediaDownloader(jdkClient());
            assertThrows(MediaDownloader.MediaDownloadException.class,
                    () -> downloader.download("http://localhost:" + port + "/file"));
        } finally {
            server.stop(0);
        }
    }

    private static MediaDownloadClient jdkClient() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return url -> {
            try {
                HttpResponse<byte[]> response = http.send(
                        HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                return Response.status(response.statusCode()).entity(response.body()).build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };
    }
}
