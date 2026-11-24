package dev.omatheusmesmo.qlawkus.tools.skillhub;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Stand-in fetch client for unit tests that construct {@link HttpSkillHub} without CDI.
 */
final class SkillHubFetchClients {

    private SkillHubFetchClients() {
    }

    static SkillHubFetchClient jdk(SkillHubConfig config) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(config.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return url -> {
            try {
                HttpResponse<InputStream> response = http.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .timeout(config.requestTimeout())
                                .header("Accept", "application/json, text/plain, */*")
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                Response.ResponseBuilder builder = Response.status(response.statusCode());
                response.headers().map().forEach(
                        (name, values) -> values.forEach(value -> builder.header(name, value)));
                return builder.entity(response.body()).build();
            } catch (IOException e) {
                throw new ProcessingException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProcessingException(e);
            }
        };
    }
}
