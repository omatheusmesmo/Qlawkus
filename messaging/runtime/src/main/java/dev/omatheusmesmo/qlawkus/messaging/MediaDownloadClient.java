package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.http.HttpRetryClassifier;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.Url;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Per-call media fetch. The configured base URL is only a mandatory default; each download
 * supplies the provider URL with {@link Url}. Redirects stay off so a platform cannot repoint
 * the request after the adapter chose a trusted URL.
 */
@RegisterRestClient(configKey = "media-download")
public interface MediaDownloadClient {

    @GET
    @Produces(MediaType.WILDCARD)
    Response get(@Url String url);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
