package dev.omatheusmesmo.qlawkus.tools.skillhub;

import dev.omatheusmesmo.qlawkus.http.HttpRetryClassifier;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import io.quarkus.rest.client.reactive.Url;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * One GET to a fully qualified URL the hub built after {@link OutboundPolicy} approved it. Redirects
 * stay off so each hop is checked again in {@link HttpSkillHub#download}.
 */
@RegisterRestClient(configKey = "skill-hub-fetch")
public interface SkillHubFetchClient {

    @GET
    @Produces(MediaType.WILDCARD)
    @ClientHeaderParam(name = "Accept", value = "application/json, text/plain, */*")
    Response get(@Url String url);

    @ClientExceptionMapper
    static RuntimeException toException(Response response) {
        return HttpRetryClassifier.classify(response);
    }
}
