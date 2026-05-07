package dev.omatheusmesmo.qlawkus.it.google;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.SheetsTool;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(GoogleWireMockProfile.class)
@ConnectWireMock
class SheetsWireMockTest {

    WireMock wiremock;

    @Inject
    @ClawTool
    SheetsTool sheetsTool;

    @Test
    void readSheet_returnsValues() {
        wiremock.register(WireMock.get(urlPathEqualTo("/v4/spreadsheets/sheet-1/values/Sheet1!A1:B2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "range": "Sheet1!A1:B2",
                                  "values": [["Name", "Age"], ["Alice", "30"]]
                                }
                                """)));

        String result = sheetsTool.readSheet("sheet-1", "Sheet1!A1:B2");

        assertTrue(result.contains("Alice"), "Should contain cell value. Got: " + result);
    }

    @Test
    void writeSheet_returnsUpdateCount() {
        wiremock.register(WireMock.put(urlPathEqualTo("/v4/spreadsheets/sheet-1/values/Sheet1!A1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "updatedCells": 1,
                                  "updatedRows": 1,
                                  "updatedColumns": 1
                                }
                                """)));

        String result = sheetsTool.writeSheet("sheet-1", "Sheet1!A1", "Header");

        assertTrue(result.contains("1 cells"), "Should report updated cells. Got: " + result);
    }

    @Test
    void updateCell_returnsSuccess() {
        wiremock.register(WireMock.put(urlPathEqualTo("/v4/spreadsheets/sheet-1/values/Sheet1!B2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "updatedCells": 1,
                                  "updatedRows": 1,
                                  "updatedColumns": 1
                                }
                                """)));

        String result = sheetsTool.updateCell("sheet-1", "Sheet1!B2", "42");

        assertTrue(result.contains("updated"), "Should confirm update. Got: " + result);
    }
}
