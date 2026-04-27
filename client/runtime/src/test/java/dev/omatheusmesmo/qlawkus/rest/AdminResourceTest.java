package dev.omatheusmesmo.qlawkus.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class AdminResourceTest {

  @Test
  void list_withoutAuth_returns401() {
    given()
        .when()
        .get("/api/admin/memory")
        .then()
        .statusCode(401);
  }

  @Test
  void list_returnsMemorySummary() {
    given()
        .auth().basic("admin", "admin123")
        .when()
        .get("/api/admin/memory")
        .then()
        .statusCode(200)
        .body("journalCount", greaterThanOrEqualTo(0))
        .body("chatMessageCount", greaterThanOrEqualTo(0));
  }

  @Test
  void listJournals_returns200() {
    given()
        .auth().basic("admin", "admin123")
        .when()
        .get("/api/admin/memory/journals")
        .then()
        .statusCode(200);
  }

  @Test
  void purge_withoutAuth_returns401() {
    given()
        .when()
        .delete("/api/admin/memory")
        .then()
        .statusCode(401);
  }

  @Test
  void purge_withoutParams_returns400() {
    given()
        .auth().basic("admin", "admin123")
        .when()
        .delete("/api/admin/memory")
        .then()
        .statusCode(400)
        .body("error", equalTo("Specify source, includeJournals=true, or all=true"));
  }

  @Test
  void purge_bySource_returns200() {
    given()
        .auth().basic("admin", "admin123")
        .queryParam("source", "semantic-extractor")
        .when()
        .delete("/api/admin/memory")
        .then()
        .statusCode(200);
  }

  @Test
  void purge_journals_returns200() {
    given()
        .auth().basic("admin", "admin123")
        .queryParam("includeJournals", "true")
        .when()
        .delete("/api/admin/memory")
        .then()
        .statusCode(200);
  }

  @Test
  void purge_all_returns204() {
    given()
        .auth().basic("admin", "admin123")
        .queryParam("all", "true")
        .when()
        .delete("/api/admin/memory")
        .then()
        .statusCode(204);
  }
}
