package dev.omatheusmesmo.qlawkus.it.google;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tools.google.drive.DriveTool;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GoogleWireMockProfile.class)
@ConnectWireMock
class DriveWireMockTest {

    WireMock wiremock;

    @Inject
    @ClawTool
    DriveTool driveTool;

    @Test
    void listFiles_returnsFileList() {
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

        String result = driveTool.listFiles(null, 10);

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
}
