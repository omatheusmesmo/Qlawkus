package dev.omatheusmesmo.qlawkus.it.google;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the packaged application, including the GraalVM native image under {@code -Pnative}, with no
 * Google OAuth client configured, which is how every distribution that bundles the Google modules
 * starts before its owner authorizes. The {@code @QuarkusTest} classes in this module always run with
 * a test client id and WireMock-backed Google APIs, so they cannot see a boot that depends on live
 * credentials. Skipped when the environment supplies a real client, since that is not the state
 * under test.
 */
@QuarkusIntegrationTest
@DisabledIfEnvironmentVariable(named = "GOOGLE_CLIENT_ID", matches = ".+")
class GoogleUnconfiguredBootIT {

    @TestHTTPResource
    URI baseUri;

    @Test
    void oauthStartExplainsThatNoClientIsConfigured() throws Exception {
        String auth = Base64.getEncoder().encodeToString("qlawkus:qlawkus-test".getBytes());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUri + "api/google/oauth/start"))
                .header("Authorization", "Basic " + auth)
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(409, response.statusCode(),
                "an agent with no Google client must refuse to start the flow, not redirect: " + response.body());
        assertTrue(response.body().contains("GOOGLE_CLIENT_ID"),
                "the refusal must name what to configure: " + response.body());
    }
}
