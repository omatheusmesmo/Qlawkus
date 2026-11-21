package dev.omatheusmesmo.qlawkus.messaging.tts;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
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

class ElevenLabsWireTest {

    private static final String URL = "/v1/text-to-speech/voice123?output_format=mp3_44100_128";
    private static final byte[] AUDIO = {(byte) 0xFF, (byte) 0xFB, 0x42};

    static final int PORT = startServer();

    static final WireMock WIREMOCK = new WireMock("localhost", PORT);

    private static int startServer() {
        String key = "qlawkus.test.wire.elevenlabs.port";
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
            .overrideRuntimeConfigKey("qlawkus.agent.state.root", "target/elevenlabs-wire/state")
            .overrideRuntimeConfigKey("qlawkus.agent.facts.root", "target/elevenlabs-wire/facts")
            .overrideRuntimeConfigKey("qlawkus.agent.episodic.root", "target/elevenlabs-wire/journals")
            .overrideRuntimeConfigKey("qlawkus.agent.working-memory.root", "target/elevenlabs-wire/working-memory")
            .overrideRuntimeConfigKey("qlawkus.skills.roots", "target/elevenlabs-wire/skills");

    @Inject
    ElevenLabsTtsClient client;

    @BeforeEach
    void reset() {
        WIREMOCK.resetMappings();
        WIREMOCK.resetRequests();
    }

    private static TtsConfig.TtsProvider provider(String apiKey, String responseFormat) {
        return TtsProviders.provider("elevenlabs", "http://localhost:" + PORT, apiKey,
                "eleven_multilingual_v2", "voice123", responseFormat);
    }

    private static void reply(String url, int status) {
        WIREMOCK.register(post(urlEqualTo(url)).willReturn(aResponse().withStatus(status).withBody(AUDIO)));
    }

    @Test
    void postsToTheVoiceEndpointWithTheApiKeyHeaderAndTheMp3OutputFormat() {
        String url = "/v1/text-to-speech/voice123?output_format=mp3_44100_128";
        reply(url, 200);

        byte[] result = client.synthesize(provider("xi-secret", "mp3"), "olá mundo");

        assertArrayEquals(AUDIO, result);
        WIREMOCK.verifyThat(1, postRequestedFor(urlEqualTo(url)));
        WIREMOCK.verifyThat(postRequestedFor(urlEqualTo(url))
                .withHeader("xi-api-key", equalTo("xi-secret"))
                .withHeader("Authorization", absent())
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Accept", equalTo("audio/mpeg"))
                .withRequestBody(equalToJson("""
                        {"text":"olá mundo","model_id":"eleven_multilingual_v2"}""")));
    }

    @Test
    void aNonMp3ResponseFormatIsPassedThroughAsTheOutputFormat() {
        String url = "/v1/text-to-speech/voice123?output_format=pcm_16000";
        reply(url, 200);

        client.synthesize(provider("xi-secret", "pcm_16000"), "hi");

        WIREMOCK.verifyThat(1, postRequestedFor(urlEqualTo(url)));
    }

    @Test
    void aMissingApiKeyFailsBeforeAnyRequest() {
        assertThrows(IllegalStateException.class, () -> client.synthesize(provider(null, "mp3"), "hi"));
        assertThrows(IllegalStateException.class, () -> client.synthesize(provider(" ", "mp3"), "hi"));

        assertTrue(WIREMOCK.getServeEvents().isEmpty());
    }

    @Test
    void aTransientFailureIsRetriedUntilTheRequestSucceeds() {
        for (int status : new int[] {503, 429}) {
            WIREMOCK.resetMappings();
            WIREMOCK.resetRequests();
            WIREMOCK.resetScenarios();
            WIREMOCK.register(post(urlEqualTo(URL)).inScenario("retry" + status)
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(status))
                    .willSetStateTo("recovered"));
            WIREMOCK.register(post(urlEqualTo(URL)).inScenario("retry" + status)
                    .whenScenarioStateIs("recovered")
                    .willReturn(aResponse().withStatus(200).withBody(AUDIO)));

            assertArrayEquals(AUDIO, client.synthesize(provider("k", "mp3"), "hi"));

            assertEquals(2, WIREMOCK.find(postRequestedFor(urlEqualTo(URL))).size());
        }
    }

    @Test
    void aTransientFailureThatNeverClearsSurfacesAsATransientHttpExceptionAfterTheRetries() {
        reply(URL, 503);

        assertThrows(TransientHttpException.class, () -> client.synthesize(provider("k", "mp3"), "hi"));

        assertEquals(4, WIREMOCK.find(postRequestedFor(urlEqualTo(URL))).size());
    }

    @Test
    void permanentStatusesAreNotRetried() {
        for (int status : new int[] {401, 403, 404}) {
            WIREMOCK.resetRequests();
            reply(URL, status);

            assertThrows(RuntimeException.class, () -> client.synthesize(provider("k", "mp3"), "hi"));

            assertEquals(1, WIREMOCK.find(postRequestedFor(urlEqualTo(URL))).size(), "status " + status);
        }
    }

    @Test
    void aPermanentFailureSurfacesTheStatusWithoutTheKeyAndIsNotRetried() {
        String url = "/v1/text-to-speech/voice123?output_format=mp3_44100_128";
        reply(url, 401);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> client.synthesize(provider("xi-secret", "mp3"), "hi"));

        assertTrue(failure.getMessage().contains("401"), failure.getMessage());
        assertFalse(failure.getMessage().contains("xi-secret"), failure.getMessage());
        assertEquals(1, WIREMOCK.getServeEvents().size());
    }
}
