package dev.omatheusmesmo.qlawkus.messaging;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
* Provider-agnostic downloader for inbound media (voice notes, attachments).
* Retries transient network failures via MicroProfile Fault Tolerance so each
* messaging adapter only needs to resolve the media URL, not the fetch logic.
*/
@ApplicationScoped
public class MediaDownloader {

    private HttpClient client;

    @PostConstruct
    void init() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    void setClient(HttpClient client) {
        this.client = client;
    }

    @Retry(maxRetries = 2, delay = 500, delayUnit = ChronoUnit.MILLIS,
            jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 40, unit = ChronoUnit.SECONDS)
    public byte[] download(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new MediaDownloadException("media download returned status " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new MediaDownloadException("media download failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaDownloadException("media download interrupted", e);
        }
    }

    public static class MediaDownloadException extends RuntimeException {
        public MediaDownloadException(String message) {
            super(message);
        }

        public MediaDownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
