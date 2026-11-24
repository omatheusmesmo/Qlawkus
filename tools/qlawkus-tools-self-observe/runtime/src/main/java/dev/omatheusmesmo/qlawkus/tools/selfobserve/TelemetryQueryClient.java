package dev.omatheusmesmo.qlawkus.tools.selfobserve;

import dev.omatheusmesmo.qlawkus.http.HttpRetryClassifier;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.Url;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Read-only GET to a fully qualified telemetry query URL built from configuration. The interface's
 * configured base URL is only a mandatory default; each call supplies the real target with {@link Url}.
 */
@RegisterRestClient(configKey = "self-observe-query")
public interface TelemetryQueryClient {

    @GET
    @Produces(MediaType.WILDCARD)
    Response get(@Url String url);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
