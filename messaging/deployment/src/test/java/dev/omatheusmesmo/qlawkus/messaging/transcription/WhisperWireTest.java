package dev.omatheusmesmo.qlawkus.messaging.transcription;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.matching.MultipartValuePatternBuilder;
import dev.omatheusmesmo.qlawkus.http.TransientHttpException;
import io.quarkus.test.QuarkusExtensionTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class WhisperWireTest {

    private static final String PATH = "/v1/audio/transcriptions";
    private static final byte[] AUDIO = {1, 2, 3, (byte) 0xFF, 0, 10, 13};

    static final int PORT = startServer();

    static final WireMock WIREMOCK = new WireMock("localhost", PORT);

    private static int startServer() {
        String key = "qlawkus.test.wire.whisper.port";
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
            .withEmptyApplication()
            .overrideConfigKey("quarkus.http.test-port", "0")
            .overrideConfigKey("quarkus.management.test-port", "0")
            .overrideRuntimeConfigKey("LLM_API_KEY", "test-key")
            .overrideRuntimeConfigKey("qlawkus.startup-thought.enabled", "false")
            .overrideRuntimeConfigKey("qlawkus.agent.state.root", "target/whisper-wire/state")
            .overrideRuntimeConfigKey("qlawkus.agent.facts.root", "target/whisper-wire/facts")
            .overrideRuntimeConfigKey("qlawkus.agent.episodic.root", "target/whisper-wire/journals")
            .overrideRuntimeConfigKey("qlawkus.agent.working-memory.root", "target/whisper-wire/working-memory")
            .overrideRuntimeConfigKey("qlawkus.skills.roots", "target/whisper-wire/skills")
            .overrideRuntimeConfigKey("qlawkus.messaging.transcription.api-key", "sk-test-secret")
            .overrideRuntimeConfigKey("qlawkus.messaging.transcription.model", "whisper-1")
            .overrideRuntimeConfigKey("qlawkus.messaging.transcription.base-url",
                    "http://localhost:" + PORT);

    @Inject
    WhisperTranscriptionService service;

    @BeforeEach
    void reset() {
        WIREMOCK.resetMappings();
        WIREMOCK.resetRequests();
    }

    private static void reply(int status, String body) {
        WIREMOCK.register(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static MultipartValuePatternBuilder part(String name) {
        return aMultipart().withName(name);
    }

    @Test
    void aVoiceNoteIsAPlainMultipartPostWithABearerKey() {
        reply(200, "{\"text\":\"nota de voz\",\"language\":\"portuguese\"}");

        String text = service.transcribe(AUDIO, "audio/ogg");

        assertEquals("nota de voz", text);
        WIREMOCK.verifyThat(1, postRequestedFor(urlEqualTo(PATH)));
        WIREMOCK.verifyThat(postRequestedFor(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer sk-test-secret"))
                .withHeader("Content-Type", matching("multipart/form-data; ?boundary=.+"))
                .withRequestBodyPart(part("file")
                        .withHeader("Content-Disposition", containing("filename=\"audio.ogg\""))
                        .withHeader("Content-Type", containing("audio/ogg"))
                        .withBody(binaryEqualTo(AUDIO)).build())
                .withRequestBodyPart(part("model").withBody(equalTo("whisper-1")).build()));
        String body = WIREMOCK.getServeEvents().get(0).getRequest().getBodyAsString();
        assertFalse(body.contains("response_format"), body);
        assertFalse(body.contains("name=\"language\""), body);
    }

    @Test
    void theVoiceSessionPathAsksForVerboseJsonAndSendsTheLanguageHintAsAnIsoCode() {
        reply(200, "{\"text\":\"oi\",\"language\":\"portuguese\"}");

        VoiceTranscriptionService.Transcription result = service.transcribe(AUDIO, "audio/wav", "pt-BR");

        assertEquals("oi", result.text());
        assertEquals("pt", result.language());
        WIREMOCK.verifyThat(postRequestedFor(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer sk-test-secret"))
                .withRequestBodyPart(part("file")
                        .withHeader("Content-Disposition", containing("filename=\"audio.wav\""))
                        .withHeader("Content-Type", containing("audio/wav"))
                        .withBody(binaryEqualTo(AUDIO)).build())
                .withRequestBodyPart(part("model").withBody(equalTo("whisper-1")).build())
                .withRequestBodyPart(part("response_format").withBody(equalTo("verbose_json")).build())
                .withRequestBodyPart(part("language").withBody(equalTo("pt")).build()));
    }

    @Test
    void theVoiceSessionPathOmitsTheLanguageFieldWithoutAHint() {
        reply(200, "{\"text\":\"hi\",\"language\":\"english\"}");

        VoiceTranscriptionService.Transcription result = service.transcribe(AUDIO, "audio/wav", null);

        assertEquals("en", result.language());
        String body = WIREMOCK.getServeEvents().get(0).getRequest().getBodyAsString();
        assertTrue(body.contains("name=\"response_format\""), body);
        assertFalse(body.contains("name=\"language\""), body);
    }

    @Test
    void anUnknownDetectedLanguageNameYieldsNoLanguageCode() {
        reply(200, "{\"text\":\"...\",\"language\":\"klingon\"}");

        assertNull(service.transcribe(AUDIO, "audio/wav", null).language());
    }

    @Test
    void unknownResponseFieldsAreIgnored() {
        reply(200, "{\"text\":\"hello\",\"duration\":1.5,\"segments\":[]}");

        assertEquals("hello", service.transcribe(AUDIO));
    }

    @Test
    void theFileNameExtensionFollowsTheMimeType() {
        reply(200, "{\"text\":\"x\"}");

        service.transcribe(AUDIO, "audio/webm");
        service.transcribe(AUDIO, "audio/mpeg");
        service.transcribe(AUDIO, "audio/mp4");
        service.transcribe(AUDIO, "audio/x-wav");
        service.transcribe(AUDIO, "audio/unknown");

        for (String extension : new String[] {"webm", "mp3", "m4a", "wav", "ogg"}) {
            WIREMOCK.verifyThat(1, postRequestedFor(urlEqualTo(PATH)).withRequestBodyPart(part("file")
                    .withHeader("Content-Disposition", containing("filename=\"audio." + extension + "\""))
                    .build()));
        }
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
                    .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                            .withBody("{\"text\":\"ok\"}")));

            assertEquals("ok", service.transcribe(AUDIO));

            WIREMOCK.verifyThat(2, postRequestedFor(urlEqualTo(PATH))
                    .withRequestBodyPart(part("file").withBody(binaryEqualTo(AUDIO)).build()));
        }
    }

    @Test
    void aTransientFailureThatNeverClearsSurfacesAsATransientHttpExceptionAfterTheRetries() {
        reply(503, "{}");

        assertThrows(TransientHttpException.class, () -> service.transcribe(AUDIO));

        assertEquals(4, WIREMOCK.find(postRequestedFor(urlEqualTo(PATH))).size());
    }

    @Test
    void permanentStatusesAreNotRetried() {
        for (int status : new int[] {401, 403, 404}) {
            WIREMOCK.resetRequests();
            reply(status, "{}");

            assertThrows(RuntimeException.class, () -> service.transcribe(AUDIO));

            assertEquals(1, WIREMOCK.find(postRequestedFor(urlEqualTo(PATH))).size(), "status " + status);
        }
    }

    @Test
    void aPermanentFailureSurfacesTheStatusWithoutTheKeyAndIsNotRetried() {
        reply(401, "{\"error\":\"Invalid API key\"}");

        RuntimeException failure = assertThrows(RuntimeException.class, () -> service.transcribe(AUDIO));

        assertTrue(failure.getMessage().contains("401"), failure.getMessage());
        assertFalse(failure.getMessage().contains("sk-test-secret"), failure.getMessage());
        assertEquals(1, WIREMOCK.find(postRequestedFor(urlEqualTo(PATH))).size());
    }
}
