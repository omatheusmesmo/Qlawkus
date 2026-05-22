package dev.omatheusmesmo.qlawkus.messaging;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

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
            byte[] result = new MediaDownloader().download("http://localhost:" + port + "/file");
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
            assertThrows(MediaDownloader.MediaDownloadException.class,
                    () -> downloader.download("http://localhost:" + port + "/file"));
        } finally {
            server.stop(0);
        }
    }
}
