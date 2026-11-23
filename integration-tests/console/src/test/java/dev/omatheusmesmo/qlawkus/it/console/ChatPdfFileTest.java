package dev.omatheusmesmo.qlawkus.it.console;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@ConnectWireMock
@TestProfile(ChatPdfFileTest.ChoosePdfModel.class)
class ChatPdfFileTest {

    private static final String ENDPOINT = "/v1/chat/completions";

    WireMock wiremock;

    public static class ChoosePdfModel implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("qlawkus.attachments.pdf-model", "primary",
                    "qlawkus.attachments.image-model", "fallback");
        }
    }

    @BeforeEach
    void stubModel() {
        wiremock.resetRequests();
        wiremock.register(post(urlEqualTo(ENDPOINT)).willReturn(okJson("""
                {"id":"test","object":"chat.completion","created":1,
                 "model":"test-model","choices":[{"index":0,"finish_reason":"stop",
                 "message":{"role":"assistant","content":"PDF sent as a file"}}]}
                """)));
    }

    @Test
    void sendsPdfFileContentThroughTheConfiguredModelAdapter() throws IOException {
        byte[] bytes = ChatAttachmentTest.textPdf();
        String data = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(bytes);

        given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .header("X-Qlawkus-Attachments", "1")
                .multiPart("attachments", "fixture.pdf", bytes, "application/pdf")
                .when().post("/api/chat")
                .then().statusCode(200).body(containsString("PDF sent as a file"));

        wiremock.verifyThat(postRequestedFor(urlEqualTo(ENDPOINT))
                .withRequestBody(matchingJsonPath("$.messages[1].content[2].file.file_data", equalTo(data)))
                .withRequestBody(notContaining("Private fixture sentence")));
    }

    @Test
    void conflictingModelTiersRefuseMixedAttachmentsBeforeContactingTheModel() throws IOException {
        given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .header("X-Qlawkus-Attachments", "1")
                .multiPart("attachments", "fixture.pdf", ChatAttachmentTest.textPdf(), "application/pdf")
                .multiPart("attachments", "pixel.png", Base64.getDecoder().decode(ChatAttachmentTest.PNG), "image/png")
                .when().post("/api/chat")
                .then().statusCode(400).body(containsString("different model tiers"));

        wiremock.verifyThat(0, postRequestedFor(urlEqualTo(ENDPOINT)));
    }
}
