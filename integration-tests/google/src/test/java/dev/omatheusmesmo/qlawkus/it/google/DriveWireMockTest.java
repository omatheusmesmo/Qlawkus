package dev.omatheusmesmo.qlawkus.it.google;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.approval.ApprovalPolicy;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tools.google.drive.DriveTool;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@ConnectWireMock
class DriveWireMockTest {

    WireMock wiremock;

    @Inject
    @QlawTool
    DriveTool driveTool;

    @Inject
    ApprovalPolicy approvalPolicy;

    @Test
    void listDriveFiles_returnsFileList() {
        wiremock.register(WireMock.get(urlPathEqualTo("/drive/v3/files"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "files": [
                                    {
                                      "id": "file-1",
                                      "name": "report.pdf",
                                      "mimeType": "application/pdf",
                                      "modifiedTime": "2026-05-10T12:00:00Z",
                                      "size": "1024",
                                      "webViewLink": "https://drive.google.com/file/d/file-1"
                                    }
                                  ]
                                }
                                """)));

        String result = driveTool.listDriveFiles(null, 10);

        assertTrue(result.contains("report.pdf"), "Should contain file name. Got: " + result);
    }

    @Test
    void uploadFile_returnsUploadedFile() {
        wiremock.register(WireMock.post(urlPathEqualTo("/upload/drive/v3/files"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "file-2",
                                  "name": "notes.txt",
                                  "webViewLink": "https://drive.google.com/file/d/file-2"
                                }
                                """)));

        String result = driveTool.uploadFile("notes.txt", "Hello world", null);

        assertTrue(result.contains("notes.txt"), "Should contain uploaded file name. Got: " + result);
    }

    @Test
    void downloadFile_returnsContent() {
        wiremock.register(WireMock.get(urlPathEqualTo("/drive/v3/files/file-1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "file-1",
                                  "name": "notes.txt",
                                  "mimeType": "text/plain",
                                  "modifiedTime": "2026-05-10T12:00:00Z",
                                  "webViewLink": "https://drive.google.com/file/d/file-1"
                                }
                                """)));

        wiremock.register(WireMock.get(urlPathEqualTo("/drive/v3/files/file-1"))
                .withQueryParam("alt", equalTo("media"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Hello world from Drive")));

        String result = driveTool.downloadFile("file-1");

        assertTrue(result.contains("Hello world from Drive"), "Should contain file content. Got: " + result);
    }

    @Test
    void shareFile_returnsPermission() {
        wiremock.register(WireMock.post(urlPathEqualTo("/drive/v3/files/file-1/permissions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "perm-1",
                                  "emailAddress": "colleague@example.com",
                                  "role": "reader"
                                }
                                """)));

        String result = driveTool.shareFile("file-1", "colleague@example.com", null);

        assertTrue(result.contains("colleague@example.com"), "Should confirm sharing. Got: " + result);
    }

    @Test
    void trashFile_marksTheFileTrashedAndNeverDeletesIt() {
        wiremock.register(WireMock.patch(urlPathEqualTo("/drive/v3/files/file-1"))
                .withRequestBody(equalToJson("{\"trashed\":true}"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "file-1", "name": "report.pdf", "trashed": true}
                                """)));

        String result = driveTool.trashFile("file-1");

        assertTrue(result.contains("report.pdf") && result.contains("trash"), "Should confirm the move. Got: " + result);
        wiremock.verifyThat(1, patchRequestedFor(urlPathEqualTo("/drive/v3/files/file-1")));
        wiremock.verifyThat(0, WireMock.deleteRequestedFor(urlPathEqualTo("/drive/v3/files/file-1")));
    }

    @Test
    void untrashFile_restoresTheFile() {
        wiremock.register(WireMock.patch(urlPathEqualTo("/drive/v3/files/file-1"))
                .withRequestBody(equalToJson("{\"trashed\":false}"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "file-1", "name": "report.pdf", "trashed": false}
                                """)));

        String result = driveTool.untrashFile("file-1");

        assertTrue(result.contains("report.pdf") && result.contains("restored"), "Should confirm the restore. Got: " + result);
    }

    @Test
    void trashFile_unknownIdSaysSoInsteadOfThrowing() {
        wiremock.register(WireMock.patch(urlPathEqualTo("/drive/v3/files/missing"))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":404,\"message\":\"File not found: missing.\"}}")));

        String result = driveTool.trashFile("missing");

        assertTrue(result.contains("Not found"), "Should say the file was not found. Got: " + result);
        assertFalse(result.contains("Exception"), "Should not leak a stack trace. Got: " + result);
    }

    @Test
    void onlyTrashFileWaitsForTheOwnerByDefault() {
        assertTrue(approvalPolicy.requires("trashFile"));
        assertFalse(approvalPolicy.requires("untrashFile"));
        assertFalse(approvalPolicy.requires("listDriveFiles"));
    }
}
