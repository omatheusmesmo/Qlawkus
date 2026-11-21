package dev.omatheusmesmo.qlawkus.it.consoleskillhub;

import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.postJson;
import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.skillMd;
import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.stubSkill;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The panel's link flow end to end against a WireMock source: the link is listed, the listed skill is
 * selected and installed, and the installed skill appears on the panel with its recorded source.
 */
@QuarkusTest
@ConnectWireMock
class InstallThroughPanelTest {

  @Inject
  SkillHubConfig config;

  WireMock wiremock;

  @BeforeEach
  void resetSources() {
    wiremock.resetToDefaultMappings();
  }

  @Test
  void aLinkIsListedThenItsSelectedSkillIsInstalledAndShownOnThePanel() {
    String path = "/panel/flow/SKILL.md";
    stubSkill(wiremock, path, skillMd("flow-panel", "Installed through the panel"));
    String source = config.baseUrl() + path;

    postJson("install/list", "{\"source\":\"" + source + "\"}")
        .then().statusCode(200)
        .body(containsString("\"name\":\"flow\""));

    postJson("install/selection", "{\"source\":\"" + source + "\",\"skills\":\"flow\"}")
        .then().statusCode(200)
        .body(containsString("flow-panel"));

    given().auth().basic(PanelSupport.USER, PanelSupport.PASSWORD)
        .when().get("/console/skills/install")
        .then().statusCode(200)
        .body(containsString("flow-panel"))
        .body(containsString(source));
  }

  @Test
  void aSourceThatCannotBeReachedIsReportedAsUnreachable() {
    postJson("install", "{\"source\":\"http://localhost:1/panel/gone/SKILL.md\"}")
        .then().statusCode(422)
        .body(containsString("\"kind\":\"unreachable\""));
  }
}
