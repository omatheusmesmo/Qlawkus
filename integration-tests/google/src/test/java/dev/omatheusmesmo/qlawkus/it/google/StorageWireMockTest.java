package dev.omatheusmesmo.qlawkus.it.google;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import dev.omatheusmesmo.qlawkus.tools.google.storage.StorageTool;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@ConnectWireMock
class StorageWireMockTest {

    WireMock wiremock;

    @Inject
    @QlawTool
    StorageTool storageTool;

    @Test
    void listBuckets_returnsBuckets() {
        wiremock.register(WireMock.get(urlPathEqualTo("/storage/v1/b"))
                .withQueryParam("project", equalTo("test-project"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "items": [
                                    {
                                      "name": "my-bucket",
                                      "location": "US",
                                      "storageClass": "STANDARD",
                                      "created": "2026-01-15T10:00:00Z"
                                    }
                                  ]
                                }
                                """)));

        String result = storageTool.listBuckets(null);

        assertTrue(result.contains("my-bucket"), "Should contain bucket name. Got: " + result);
    }

    @Test
    void uploadObject_returnsObjectMetadata() {
        wiremock.register(WireMock.post(urlPathEqualTo("/storage/v1/b/my-bucket/o"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "name": "report.txt",
                                  "bucket": "my-bucket",
                                  "size": 12
                                }
                                """)));

        String result = storageTool.uploadObject("my-bucket", "report.txt", "Hello from GCS");

        assertTrue(result.contains("report.txt"), "Should contain object name. Got: " + result);
    }

    @Test
    void downloadObject_returnsContent() {
        wiremock.register(WireMock.get(urlPathEqualTo("/storage/v1/b/my-bucket/o/report.txt"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "name": "report.txt",
                                  "bucket": "my-bucket",
                                  "size": 14
                                }
                                """)));

        wiremock.register(WireMock.get(urlPathEqualTo("/storage/v1/b/my-bucket/o/report.txt"))
                .withQueryParam("alt", equalTo("media"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("Hello from GCS")));

        String result = storageTool.downloadObject("my-bucket", "report.txt");

        assertTrue(result.contains("Hello from GCS"), "Should contain object content. Got: " + result);
    }
}
