package dev.omatheusmesmo.qlawkus.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class ApiResourceTest {

  @Test
  void chat_withoutAuth_returns401() {
    given()
      .contentType(ContentType.JSON)
      .body("{\"message\": \"hello\"}")
      .when()
      .post("/api/chat")
      .then()
      .statusCode(401);
  }

  @Test
  void chat_withInvalidCredentials_returns401() {
    given()
      .auth().basic("qlawkus", "wrongpassword")
      .contentType(ContentType.JSON)
      .body("{\"message\": \"hello\"}")
      .when()
      .post("/api/chat")
      .then()
      .statusCode(401);
  }

  @Test
  void chat_withEmptyMessage_returns400() {
    given()
      .auth().basic("qlawkus", "qlawkus-test")
      .contentType(ContentType.JSON)
      .body("{\"message\": \"\"}")
      .when()
      .post("/api/chat")
      .then()
      .statusCode(400);
  }
}
