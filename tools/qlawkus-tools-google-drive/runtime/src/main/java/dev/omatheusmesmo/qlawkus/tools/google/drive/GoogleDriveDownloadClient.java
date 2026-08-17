package dev.omatheusmesmo.qlawkus.tools.google.drive;

import dev.omatheusmesmo.qlawkus.http.HttpRetryClassifier;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import dev.omatheusmesmo.qlawkus.tools.google.auth.GoogleAuthHeadersFilter;
import dev.omatheusmesmo.qlawkus.tools.google.drive.model.DriveFile;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.time.temporal.ChronoUnit;

/**
 * {@code @Retry} covers one API call, not one tool operation. Only {@link TransientHttpException}
 * (429, 5xx) and {@code ProcessingException} (network-level failure) are retried; every other
 * status - 401 will never pass, for instance - propagates immediately.
 */
@Path("/drive/v3/files")
@RegisterRestClient(configKey = "google-drive-download", baseUri = "https://www.googleapis.com")
@RegisterProvider(GoogleAuthHeadersFilter.class)
@Retry(maxRetries = 3, delay = 500, delayUnit = ChronoUnit.MILLIS,
        jitter = 200, jitterDelayUnit = ChronoUnit.MILLIS,
        retryOn = {TransientHttpException.class, jakarta.ws.rs.ProcessingException.class})
@ExponentialBackoff(maxDelay = 8000, maxDelayUnit = ChronoUnit.MILLIS)
public interface GoogleDriveDownloadClient {

    @GET
    @Path("/{fileId}")
    String downloadFile(
            @PathParam("fileId") String fileId,
            @QueryParam("alt") String alt);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
