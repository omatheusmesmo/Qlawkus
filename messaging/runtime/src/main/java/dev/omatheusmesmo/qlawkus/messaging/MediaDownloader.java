package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.attachment.AttachmentRejected;
import dev.omatheusmesmo.qlawkus.attachment.ChatAttachment;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.net.URI;
import java.time.temporal.ChronoUnit;

/**
 * Provider-agnostic downloader for inbound media (voice notes, attachments).
 * Retries transient network failures via MicroProfile Fault Tolerance so each
 * messaging adapter only needs to resolve the media URL, not the fetch logic.
 */
@ApplicationScoped
public class MediaDownloader {

    private final MediaDownloadClient client;

    @Inject
    MediaDownloader(@RestClient MediaDownloadClient client) {
        this.client = client;
    }

    @Retry(maxRetries = 2, delay = 500, delayUnit = ChronoUnit.MILLIS,
            jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
            retryOn = TransientHttpException.class)
    @Timeout(value = 40, unit = ChronoUnit.SECONDS)
    public byte[] download(String url) {
        Response response = open(url);
        try {
            if (response.getStatus() != 200) {
                throw new MediaDownloadException("media download returned status " + response.getStatus());
            }
            return response.readEntity(byte[].class);
        } finally {
            response.close();
        }
    }

    /**
     * Channels supply only a trusted platform URL. Reject redirects and enforce the byte
     * limit during receipt, including when the platform omits Content-Length.
     */
    @Timeout(value = 30, unit = ChronoUnit.SECONDS)
    public byte[] downloadAttachment(URI uri) {
        Response response;
        try {
            response = open(uri.toString());
        } catch (TransientHttpException e) {
            throw new AttachmentRejected("Could not download the attachment from the messaging provider.");
        }
        try {
            if (response.getStatus() != 200) {
                throw new AttachmentRejected("Could not download the attachment from the messaging provider.");
            }
            byte[] bytes = response.readEntity(byte[].class);
            ChatAttachment.checkSize(bytes.length);
            return bytes;
        } catch (AttachmentRejected e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AttachmentRejected("Could not download the attachment within the size limit.");
        } finally {
            response.close();
        }
    }

    private Response open(String url) {
        try {
            return client.get(url);
        } catch (WebApplicationException e) {
            return e.getResponse();
        } catch (ProcessingException e) {
            throw new TransientHttpException("media download failed", e);
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
