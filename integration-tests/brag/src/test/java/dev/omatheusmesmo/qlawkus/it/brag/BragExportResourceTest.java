package dev.omatheusmesmo.qlawkus.it.brag;

import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tools.brag.BragEntry;
import dev.omatheusmesmo.qlawkus.tools.brag.BragTool;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@Execution(ExecutionMode.SAME_THREAD)
class BragExportResourceTest {

    @InjectMock
    ChatModel chatModel;

    @Inject
    @ClawTool
    BragTool bragTool;

    @AfterEach
    @Transactional
    void cleanup() {
        BragEntry.deleteAll();
    }

    @Test
    void export_returnsMarkdownReportWithAuth() {
        when(chatModel.chat(anyString())).thenReturn("Improved search latency");

        bragTool.addAchievement("Changed database index", "2026-05-07", "search-service");

        RestAssured.given()
                .auth().basic("qlawkus", "qlawkus-test")
                .when()
                .get("/api/brag/export?startDate=2026-01-01&endDate=2026-12-31")
                .then()
                .statusCode(200)
                .contentType("text/markdown")
                .body(containsString("# Brag Document"))
                .body(containsString("Changed database index"))
                .body(containsString("search-service"));
    }

    @Test
    void export_requiresAuthentication() {
        RestAssured.given()
                .when()
                .get("/api/brag/export")
                .then()
                .statusCode(401);
    }

    @Test
    void export_returnsNoAchievementsWhenEmpty() {
        RestAssured.given()
                .auth().basic("qlawkus", "qlawkus-test")
                .when()
                .get("/api/brag/export?startDate=2020-01-01&endDate=2020-12-31")
                .then()
                .statusCode(200)
                .body(containsString("No achievements found"));
    }
}
