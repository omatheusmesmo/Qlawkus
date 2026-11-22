package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.attachment.AttachmentRejected;
import dev.omatheusmesmo.qlawkus.attachment.ChatAttachment;
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
            .followRedirects(HttpClient.Redirect.NEVER)
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

    /**
     * Channels supply only a trusted platform URL. Reject redirects and enforce the byte
     * limit during receipt, including when the platform omits Content-Length.
     */
    @Timeout(value = 30, unit = ChronoUnit.SECONDS)
    public byte[] downloadAttachment(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).build();
        try {
            var response = client.send(request, metadata -> metadata.statusCode() == 200
                    ? HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(),
                            ChatAttachment.MAX_BYTES).apply(metadata)
                    : HttpResponse.BodySubscribers.replacing(null));
            if (response.statusCode() == 200) {
                ChatAttachment.checkSize(response.body().length);
                return response.body();
            }
        } catch (IOException e) {
            throw new AttachmentRejected("Could not download the attachment within the size limit.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new AttachmentRejected("Could not download the attachment from the messaging provider.");
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
