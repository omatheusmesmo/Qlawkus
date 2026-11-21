package dev.omatheusmesmo.qlawkus.messaging.tts;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HttpTtsWireTest {

    private static final String PATH = "/v1/audio/speech";
    private static final byte[] AUDIO = {(byte) 0xFF, (byte) 0xFB, 0x10, 0x20};

    static final int PORT = startServer();

    static final WireMock WIREMOCK = new WireMock("localhost", PORT);

    private static int startServer() {
        String key = "qlawkus.test.wire.http-tts.port";
        String existing = System.getProperty(key);
        if (existing != null) {
            return Integer.parseInt(existing);
        }
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.setProperty(key, String.valueOf(server.port()));
        return server.port();
    }

    @RegisterExtension
    static final QuarkusExtensionTest app = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClass(TtsProviders.class))
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.management.test-port", "0")
            .overrideRuntimeConfigKey("LLM_API_KEY", "test-key")
            .overrideRuntimeConfigKey("qlawkus.startup-thought.enabled", "false")
            .overrideRuntimeConfigKey("qlawkus.agent.state.root", "target/http-tts-wire/state")
            .overrideRuntimeConfigKey("qlawkus.agent.facts.root", "target/http-tts-wire/facts")
            .overrideRuntimeConfigKey("qlawkus.agent.episodic.root", "target/http-tts-wire/journals")
            .overrideRuntimeConfigKey("qlawkus.agent.working-memory.root", "target/http-tts-wire/working-memory")
            .overrideRuntimeConfigKey("qlawkus.skills.roots", "target/http-tts-wire/skills");

    @Inject
    HttpTtsClient client;

    @BeforeEach
    void reset() {
        WIREMOCK.resetMappings();
        WIREMOCK.resetRequests();
    }

    private static TtsConfig.TtsProvider provider(String apiKey) {
        return TtsProviders.provider("openai", "http://localhost:" + PORT, apiKey, "tts-1", "pt_br", "mp3");
    }

    private static void reply(int status) {
        WIREMOCK.register(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(status).withBody(AUDIO)));
    }

    @Test
    void postsTheSpeechJsonWithABearerKeyAndReturnsTheAudioBytes() {
        reply(200);

        byte[] result = client.synthesize(provider("tts-secret"), "olá mundo");

        assertArrayEquals(AUDIO, result);
        WIREMOCK.verifyThat(1, postRequestedFor(urlEqualTo(PATH)));
        WIREMOCK.verifyThat(postRequestedFor(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer tts-secret"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        {"model":"tts-1","input":"olá mundo","voice":"pt_br","response_format":"mp3"}""")));
    }

    @Test
    void sendsNoAuthorizationHeaderWithoutAKey() {
        reply(200);

        client.synthesize(provider(""), "hi");
        client.synthesize(provider(null), "hi");

        WIREMOCK.verifyThat(2, postRequestedFor(urlEqualTo(PATH)).withHeader("Authorization", absent()));
    }

    @Test
    void aTransientFailureIsRetriedUntilTheRequestSucceeds() {
        for (int status : new int[] {503, 429}) {
            WIREMOCK.resetMappings();
            WIREMOCK.resetRequests();
            WIREMOCK.resetScenarios();
            WIREMOCK.register(post(urlEqualTo(PATH)).inScenario("retry" + status)
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(status))
                    .willSetStateTo("recovered"));
            WIREMOCK.register(post(urlEqualTo(PATH)).inScenario("retry" + status)
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse().withStatus(200).withBody(AUDIO)));

            assertArrayEquals(AUDIO, client.synthesize(provider("k"), "hi"));

            assertEquals(2, WIREMOCK.find(postRequestedFor(urlEqualTo(PATH))).size());
        }
    }

    @Test
    void aTransientFailureThatNeverClearsSurfacesAsATransientHttpExceptionAfterTheRetries() {
        reply(503);

        assertThrows(TransientHttpException.class, () -> client.synthesize(provider("k"), "hi"));

        assertEquals(4, WIREMOCK.find(postRequestedFor(urlEqualTo(PATH))).size());
    }

    @Test
    void permanentStatusesAreNotRetried() {
        for (int status : new int[] {401, 403, 404}) {
            WIREMOCK.resetRequests();
            reply(status);

            assertThrows(RuntimeException.class, () -> client.synthesize(provider("k"), "hi"));

            assertEquals(1, WIREMOCK.find(postRequestedFor(urlEqualTo(PATH))).size(), "status " + status);
        }
    }

    @Test
    void aPermanentFailureSurfacesTheStatusWithoutTheKeyAndIsNotRetried() {
        reply(400);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> client.synthesize(provider("tts-secret"), "hi"));

        assertTrue(failure.getMessage().contains("400"), failure.getMessage());
        assertFalse(failure.getMessage().contains("tts-secret"), failure.getMessage());
        assertEquals(1, WIREMOCK.getServeEvents().size());
    }
}
