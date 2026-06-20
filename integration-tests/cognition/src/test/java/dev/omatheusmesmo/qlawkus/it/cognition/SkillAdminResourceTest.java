package dev.omatheusmesmo.qlawkus.it.cognition;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.chat.ChatModel;
import dev.omatheusmesmo.qlawkus.skill.Skill;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the skill admin REST surface over HTTP with basic auth: list, get (200/404), delete
 * (204/404), pin (200/404), lifecycle, and curate. The model is mocked so the curate endpoint is
 * deterministic. This is the only test that covers the resource wiring, status codes, and the
 * {@code @Authenticated} gate.
 */
@QuarkusTest
class SkillAdminResourceTest {

  private static final String USER = "qlawkus";
  private static final String PASS = "qlawkus-test";
  private static final String BASE = "/api/admin/skills";

  @InjectMock
  ChatModel chatModel;

  @Inject
  SkillStore skillStore;

  @BeforeEach
  void seed() {
    skillStore.save(new Skill("rest-skill", "a skill for the REST test", "1. step"));
  }

  @AfterEach
  void cleanup() {
    skillStore.delete("rest-skill");
  }

  @Test
  void unauthenticated_isRejected() {
    given().when().get(BASE).then().statusCode(401);
  }

  @Test
  void list_returnsIndex() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get(BASE)
        .then().statusCode(200);
  }

  @Test
  void get_existing_and_missing() {
    given().auth().preemptive().basic(USER, PASS)
        .when().get(BASE + "/rest-skill")
        .then().statusCode(200)
        .body("description", equalTo("a skill for the REST test"));

    given().auth().preemptive().basic(USER, PASS)
        .when().get(BASE + "/does-not-exist")
        .then().statusCode(404);
  }

  @Test
  void delete_existing_thenMissing() {
    given().auth().preemptive().basic(USER, PASS)
        .when().delete(BASE + "/rest-skill")
        .then().statusCode(204);

    given().auth().preemptive().basic(USER, PASS)
        .when().delete(BASE + "/rest-skill")
        .then().statusCode(404);
  }

  @Test
  void pin_existing_and_missing() {
    given().auth().preemptive().basic(USER, PASS)
        .when().post(BASE + "/rest-skill/pin?pinned=true")
        .then().statusCode(200)
        .body("pinned", equalTo(true));

    given().auth().preemptive().basic(USER, PASS)
        .when().post(BASE + "/does-not-exist/pin")
        .then().statusCode(404);
  }

  @Test
  void lifecycle_returnsTransitionCount() {
    given().auth().preemptive().basic(USER, PASS)
        .when().post(BASE + "/lifecycle")
        .then().statusCode(200)
        .body("$", hasKey("transitioned"));
  }

  @Test
  void curate_returnsRemovedCount() {
    when(chatModel.chat(anyString())).thenReturn("NONE");

    given().auth().preemptive().basic(USER, PASS)
        .when().post(BASE + "/curate")
        .then().statusCode(200)
        .body("$", hasKey("removed"));
  }
}
