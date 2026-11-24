package dev.omatheusmesmo.qlawkus.it.console;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@ConnectWireMock
class ChatAttachmentTest {
    private static final String MODEL_ENDPOINT = "/v1/chat/completions";
    static final String PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jErkAAAAASUVORK5CYII=";

    WireMock wiremock;

    @Inject
    WorkingMemoryStore memory;

    @BeforeEach
    void stubModel() {
        wiremock.resetRequests();
        wiremock.register(post(urlEqualTo(MODEL_ENDPOINT)).willReturn(okJson("""
                {"id":"test","object":"chat.completion","created":1,
                 "model":"test-model","choices":[{"index":0,"finish_reason":"stop",
                 "message":{"role":"assistant","content":"Attachment answer"}}]}
                """)));
    }

    @Test
    void pdfTextReachesTheModelWithoutEnteringConversationMemory() throws IOException {
        var before = memory.getMessages(ConversationId.SHARED);
        given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .header("X-Qlawkus-Attachments", "1")
                .multiPart("message", "What does the document say?")
                .multiPart("attachments", "owner-only.pdf", textPdf(), "application/pdf")
                .when().post("/api/chat")
                .then().statusCode(200)
                .body(containsString("Attachment answer"))
                .body(containsString("event:done"));
        wiremock.verifyThat(postRequestedFor(urlEqualTo(MODEL_ENDPOINT))
                .withRequestBody(containing("Attachment 1 (application/pdf) extracted text"))
                .withRequestBody(containing("Private fixture sentence"))
                .withRequestBody(notContaining("owner-only.pdf")));
        assertEquals(before, memory.getMessages(ConversationId.SHARED));
    }

    @Test
    void pngUsesTheProviderMultimodalSchema() {
        given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .header("X-Qlawkus-Attachments", "1")
                .multiPart("attachments", "pixel.png", Base64.getDecoder().decode(PNG), "image/png")
                .when().post("/api/chat")
                .then().statusCode(200).body(containsString("Attachment answer"));

        wiremock.verifyThat(postRequestedFor(urlEqualTo(MODEL_ENDPOINT))
                .withRequestBody(matchingJsonPath("$.messages[1].content[?(@.type == 'image_url')]"))
                .withRequestBody(containing("data:image/png;base64," + PNG))
                .withRequestBody(containing("Describe these attachments.")));
    }

    @Test
    void anUploadAcceptsEveryToolProgressModeAndRejectsAnUnknownOne() {
        byte[] bytes = Base64.getDecoder().decode(PNG);
        for (String mode : new String[] {"off", "STATUS", "preview", "verbose"}) {
            given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                    .header("X-Qlawkus-Attachments", "1")
                    .multiPart("toolProgress", mode)
                    .multiPart("attachments", "pixel.png", bytes, "image/png")
                    .when().post("/api/chat")
                    .then().statusCode(200).body(containsString("event:done"));
        }
        wiremock.resetRequests();

        given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .header("X-Qlawkus-Attachments", "1")
                .multiPart("toolProgress", "loud")
                .multiPart("attachments", "pixel.png", bytes, "image/png")
                .when().post("/api/chat")
                .then().statusCode(400);
        wiremock.verifyThat(0, postRequestedFor(urlEqualTo(MODEL_ENDPOINT)));
    }

    @Test
    void requiresAnAuthenticatedExplicitUpload() {
        byte[] bytes = Base64.getDecoder().decode(PNG);
        given().header("X-Qlawkus-Attachments", "1")
                .multiPart("attachments", "pixel.png", bytes, "image/png")
                .when().post("/api/chat").then().statusCode(401);

        given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .multiPart("attachments", "pixel.png", bytes, "image/png")
                .when().post("/api/chat").then().statusCode(400);
        wiremock.verifyThat(0, postRequestedFor(urlEqualTo(MODEL_ENDPOINT)));
    }

    @Test
    void rejectsMislabeledAndOverBudgetUploadsBeforeMakingAModelCall() {
        var upload = given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .header("X-Qlawkus-Attachments", "1");
        upload.multiPart("attachments", "pixel.pdf", Base64.getDecoder().decode(PNG), "application/pdf")
                .when().post("/api/chat").then().statusCode(400)
                .body(containsString("does not match"));

        given().auth().preemptive().basic("qlawkus", "qlawkus-test")
                .header("X-Qlawkus-Attachments", "1")
                .multiPart("attachments", "oversize.pdf", new byte[2 * 1024 * 1024 + 1], "application/pdf")
                .when().post("/api/chat").then().statusCode(400)
                .body(containsString("2 MiB"));
        wiremock.verifyThat(0, postRequestedFor(urlEqualTo(MODEL_ENDPOINT)));
    }

    static byte[] textPdf() throws IOException {
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var text = new PDPageContentStream(document, page)) {
                text.beginText();
                text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                text.newLineAtOffset(50, 700);
                text.showText("Private fixture sentence");
                text.endText();
            }
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
