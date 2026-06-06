package dev.omatheusmesmo.qlawkus.messaging;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
            MediaDownloader downloader = new MediaDownloader();
            downloader.setClient(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
            byte[] result = downloader.download("http://localhost:" + port + "/file");
            assertArrayEquals(payload, result);
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
            MediaDownloader downloader = new MediaDownloader();
            downloader.setClient(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build());
            assertThrows(MediaDownloader.MediaDownloadException.class,
                    () -> downloader.download("http://localhost:" + port + "/file"));
        } finally {
            server.stop(0);
        }
    }
}
