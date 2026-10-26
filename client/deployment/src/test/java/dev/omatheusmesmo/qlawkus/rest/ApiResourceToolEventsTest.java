package dev.omatheusmesmo.qlawkus.rest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.agent.AgentEvent;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.agent.ToolActivityEvent;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import java.util.ArrayList;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the typed events {@code POST /api/chat} adds next to the plain text events: a tool call made
 * by the agent surfaces as {@code tool-start} then {@code tool-end}, a {@code done} event closes the
 * turn, and the unnamed text events existing clients read stay exactly as they were. The tool is
 * declared on the AI service itself, so this also proves the seam sees tools that never pass through
 * {@code QlawToolProvider}.
 */
@QuarkusTest
@ConnectWireMock
class ApiResourceToolEventsTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String LONG_ARGUMENT = "skill-" + "x".repeat(120);

  WireMock wiremock;

  /**
   * Unique per test and carried in the user message, so a stub answers only this test's turn: the
   * agent's background extractors call the model after a turn too, and must not consume its script.
   */
  private String token;

  @BeforeEach
  void stubAToolTurn() {
    wiremock.resetMappings();
    wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/embed"))
        .willReturn(WireMock.okJson(embedJson())));
    wiremock.register(WireMock.post(WireMock.urlEqualTo("/v1/embeddings"))
        .willReturn(WireMock.okJson(openAiEmbeddingJson())));
    token = "turn-" + UUID.randomUUID();
    wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(10)
        .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/x-ndjson")
            .withBody(textNdjson("unrelated"))));
    wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(2)
        .withRequestBody(WireMock.containing(token))
        .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/x-ndjson")
            .withBody(toolCallNdjson())));
    wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(1)
        .withRequestBody(WireMock.containing(token))
        .withRequestBody(WireMock.matching("(?s).*" + Pattern.quote(token) + ".*\"role\"\\s*:\\s*\"tool\".*"))
        .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/x-ndjson")
            .withBody(textNdjson("All done."))));
  }

  @Test
  void aToolCallStreamsStartThenEndThenDoneAndTextStaysUnnamed() {
    List<SseEvent> events = chat("{\"message\": \"load the skill\"}");

    assertEquals(List.of("tool-start", "tool-end", "message", "done"), names(events),
        "a turn that calls one tool is tool-start, tool-end, the reply, then done: " + events);
    SseEvent text = events.get(2);
    assertEquals("All done.", text.data(), "the text event keeps the bare data: line and no event name");

    JsonNode start = json(events.get(0));
    assertEquals("viewSkill", start.get("name").asText());
    assertTrue(start.get("id").asText().length() > 0);
    JsonNode end = json(events.get(1));
    assertEquals(start.get("id").asText(), end.get("id").asText(), "start and end of one call share the id");
    assertEquals("viewSkill", end.get("name").asText());
    assertTrue(end.get("ok").asBoolean());
    assertTrue(end.get("durationMs").asLong() >= 0);
    assertFalse(end.has("result"), "a tool result can be large and carry third-party text, so it never streams");

    JsonNode done = json(events.get(3));
    assertEquals(1, done.get("toolCalls").asInt());
    assertTrue(done.get("durationMs").asLong() >= 0);
  }

  @Test
  void previewIsTheDefaultAndTruncatesArgumentsToFortyCharacters() {
    JsonNode start = json(chat("{\"message\": \"load the skill\"}").get(0));

    String detail = start.get("arguments").asText();
    assertTrue(detail.length() <= 43, "preview keeps 40 characters plus the marker: " + detail);
    assertTrue(detail.contains("skill-xxx"));
  }

  @Test
  void statusSendsNamesOnly() {
    JsonNode start = json(chat("{\"message\": \"load the skill\", \"toolProgress\": \"status\"}").get(0));

    assertEquals("viewSkill", start.get("name").asText());
    assertFalse(start.has("arguments"), "status mode never sends argument detail");
  }

  @Test
  void verboseSendsArgumentsTruncatedToThreeHundredCharacters() {
    JsonNode start = json(chat("{\"message\": \"load the skill\", \"toolProgress\": \"verbose\"}").get(0));

    String arguments = start.get("arguments").asText();
    assertTrue(arguments.contains(LONG_ARGUMENT), "verbose carries the whole argument that fits");
    assertTrue(arguments.length() <= 303);
  }

  @Test
  void offSendsNoToolEventsButTheDoneEventStillCountsThem() {
    List<SseEvent> events = chat("{\"message\": \"load the skill\", \"toolProgress\": \"off\"}");

    assertEquals(List.of("message", "done"), names(events));
    assertEquals(1, json(events.get(1)).get("toolCalls").asInt());
  }

  @Test
  void textEventsKeepTheFramingExistingClientsParse() {
    String stream = rawChat("{\"message\": \"load the skill\", \"toolProgress\": \"off\"}");

    assertTrue(stream.contains("data:All done.\n\n"), "a text event is a bare data: line: " + stream);
    assertFalse(stream.toLowerCase().contains("content-type"),
        "no per-event header may precede the data: line: " + stream);
    assertTrue(stream.indexOf("event:done") > stream.indexOf("data:All done."));
  }

  @Test
  void aSecretInToolArgumentsNeverStreams() {
    String secret = "ghp_" + "a".repeat(36);
    wiremock.register(WireMock.post(WireMock.urlEqualTo("/api/chat")).atPriority(0)
        .withRequestBody(WireMock.matching("(?s)^(?!.*" + Pattern.quote(token) + ".*\"role\"\\s*:\\s*\"tool\").*"
            + Pattern.quote(token) + ".*"))
        .willReturn(WireMock.aResponse().withHeader("Content-Type", "application/x-ndjson")
            .withBody(toolCallNdjson("{\"name\":\"" + secret + "\"}"))));

    String stream = rawChat("{\"message\": \"load the skill\", \"toolProgress\": \"verbose\"}");

    assertFalse(stream.contains(secret), "a credential in tool arguments must never reach the client: " + stream);
    assertTrue(stream.contains("[redacted]"), "the argument is still shown, with the credential masked: " + stream);
  }

  @Test
  void everyToolCallIsAlsoFiredAsACdiEventWithTheConversationId() {
    ToolActivityRecorder.EVENTS.clear();

    chat("{\"message\": \"load the skill\", \"toolProgress\": \"off\"}");

    assertEquals(2, ToolActivityRecorder.EVENTS.size(), "an observer sees a call even when the stream hides it");
    ToolActivityEvent first = ToolActivityRecorder.EVENTS.get(0);
    assertEquals(ConversationId.SHARED, first.conversationId());
    assertTrue(first.event() instanceof AgentEvent.ToolStart);
    assertTrue(ToolActivityRecorder.EVENTS.get(1).event() instanceof AgentEvent.ToolEnd);
  }

  @ApplicationScoped
  static class ToolActivityRecorder {

    static final List<ToolActivityEvent> EVENTS = new CopyOnWriteArrayList<>();

    void on(@Observes ToolActivityEvent event) {
      EVENTS.add(event);
    }
  }

  private String rawChat(String body) {
    return given().auth().basic("qlawkus", "qlawkus-test")
        .contentType("application/json").accept("text/event-stream")
        .body(body.replace("load the skill", "load the skill " + token))
        .when().post("/api/chat")
        .then().statusCode(200)
        .extract().asString();
  }

  private List<SseEvent> chat(String body) {
    return parse(rawChat(body));
  }

  private static JsonNode json(SseEvent event) {
    try {
      return JSON.readTree(event.data());
    } catch (Exception e) {
      throw new AssertionError("event data is not JSON: " + event, e);
    }
  }

  private static List<String> names(List<SseEvent> events) {
    return events.stream().map(SseEvent::name).toList();
  }

  private static List<SseEvent> parse(String stream) {
    List<SseEvent> events = new ArrayList<>();
    StringBuilder pendingText = new StringBuilder();
    for (String block : stream.split("\r?\n\r?\n")) {
      String name = "message";
      List<String> data = new ArrayList<>();
      for (String line : block.split("\r?\n", -1)) {
        if (line.startsWith("event:")) {
          name = line.substring(6).strip();
        } else if (line.startsWith("data:")) {
          data.add(line.substring(5));
        }
      }
      if (data.isEmpty()) {
        continue;
      }
      if (name.equals("message")) {
        pendingText.append(String.join("\n", data));
        continue;
      }
      if (pendingText.length() > 0) {
        events.add(new SseEvent("message", pendingText.toString(), null));
        pendingText.setLength(0);
      }
      events.add(new SseEvent(name, String.join("\n", data), null));
    }
    if (pendingText.length() > 0) {
      events.add(new SseEvent("message", pendingText.toString(), null));
    }
    return events;
  }

  private record SseEvent(String name, String data, String id) {
  }

  private static String toolCallNdjson() {
    return toolCallNdjson("{\"name\":\"" + LONG_ARGUMENT + "\"}");
  }

  private static String toolCallNdjson(String argumentsJson) {
    String ts = "2026-04-25T00:00:00Z";
    return "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts + "\",\"message\":{\"role\":\"assistant\","
        + "\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"viewSkill\",\"arguments\":" + argumentsJson
        + "}}]},\"done\":false}\n"
        + "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts
        + "\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\","
        + "\"prompt_eval_count\":5,\"eval_count\":3}\n";
  }

  private static String textNdjson(String text) {
    String ts = "2026-04-25T00:00:01Z";
    return "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts + "\",\"message\":{\"role\":\"assistant\","
        + "\"content\":\"" + text + "\"},\"done\":false}\n"
        + "{\"model\":\"gemma4:e2b\",\"created_at\":\"" + ts
        + "\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\","
        + "\"prompt_eval_count\":9,\"eval_count\":4}\n";
  }

  private static String embedJson() {
    return "{\"model\":\"nomic-embed-text\",\"embeddings\":[" + vector(768) + "],\"total_duration\":0}";
  }

  private static String openAiEmbeddingJson() {
    return "{\"data\":[{\"embedding\":" + vector(1024)
        + ",\"index\":0}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
  }

  private static String vector(int size) {
    StringBuilder embedding = new StringBuilder("[");
    for (int i = 0; i < size; i++) {
      if (i > 0) {
        embedding.append(",");
      }
      embedding.append("0.01");
    }
    return embedding.append("]").toString();
  }
}
