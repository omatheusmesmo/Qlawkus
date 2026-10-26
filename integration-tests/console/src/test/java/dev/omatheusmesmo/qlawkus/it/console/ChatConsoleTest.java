package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Exercises the chat page over HTTP: it is gated by the shared {@code @Authenticated} mechanism,
 * renders the form and loads its script from the console's static resources, and never carries
 * inline script. The streaming itself runs in the browser against {@code POST /api/chat}.
 */
@QuarkusTest
class ChatConsoleTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";

  @Test
  void page_requiresAuthentication() {
    given().when().get("/console/chat").then().statusCode(401);
  }

  @Test
  void page_rendersTheChatFormAndScriptReference() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console/chat")
        .then().statusCode(200)
        .body(containsString("<h1>Chat</h1>"))
        .body(containsString("id=\"chat-form\""))
        .body(containsString("data-endpoint=\"/api/chat\""))
        .body(containsString("data-tool-progress=\"preview\""))
        .body(containsString("<textarea"))
        .body(containsString("src=\"/console/chat.js\""))
        .body(not(containsString("<script>")));
  }

  @Test
  void sharedNav_linksTheChatPage() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get("/console")
        .then().statusCode(200)
        .body(containsString("href=\"/console/chat\""));
  }

  @Test
  void script_isServedAndRendersModelTextWithoutInnerHtml() {
    String script = given().when().get("/console/chat.js")
        .then().statusCode(200)
        .extract().asString();
    assertTrue(script.contains("textContent"));
    assertFalse(script.contains("innerHTML"));
  }
}
