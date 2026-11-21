package dev.omatheusmesmo.qlawkus.it.consoleskillhub;

import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.postJson;
import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.postText;
import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.skillMd;
import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.stubSkill;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.omatheusmesmo.qlawkus.tools.skillhub.SkillHubConfig;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The installed-skills list: a link-installed skill shows its recorded source and install time and offers
 * update and uninstall, a pasted skill has no source to update from, and skill text is escaped on the page.
 */
@QuarkusTest
@ConnectWireMock
class InstalledSkillsPanelTest {

  @Inject
  SkillHubConfig config;

  WireMock wiremock;

  @BeforeEach
  void resetSources() {
    wiremock.resetToDefaultMappings();
  }

  @Test
  void aLinkInstalledSkillShowsItsSourceAndOffersUpdateAndUninstall() {
    stubSkill(wiremock, "/panel/linked/SKILL.md", skillMd("linked-panel", "Linked from a URL"));
    String source = config.baseUrl() + "/panel/linked/SKILL.md";
    postJson("install", "{\"source\":\"" + source + "\"}").then().statusCode(200);

    given().auth().basic(PanelSupport.USER, PanelSupport.PASSWORD)
        .when().get("/console/skills/install")
        .then().statusCode(200)
        .body(containsString("linked-panel"))
        .body(containsString(source))
        .body(containsString("hx-post=\"/api/admin/skills/install/update/linked-panel\""))
        .body(containsString("hx-delete=\"/api/admin/skills/linked-panel\""));
  }

  @Test
  void aPastedSkillHasNoSourceAndNoUpdateAction() {
    postText("install/text", skillMd("pasted-panel", "Pasted by hand")).then().statusCode(200);

    given().auth().basic(PanelSupport.USER, PanelSupport.PASSWORD)
        .when().get("/console/skills/install")
        .then().statusCode(200)
        .body(containsString("pasted-panel"))
        .body(containsString("pasted or written by the agent"))
        .body(not(containsString("hx-post=\"/api/admin/skills/install/update/pasted-panel\"")));
  }

  @Test
  void skillTextIsEscapedNeverRenderedAsMarkup() {
    postText("install/text", skillMd("markup-panel", "<img src=x onerror=alert(1)>")).then().statusCode(200);

    given().auth().basic(PanelSupport.USER, PanelSupport.PASSWORD)
        .when().get("/console/skills/install")
        .then().statusCode(200)
        .body(containsString("&lt;img src=x onerror=alert(1)&gt;"))
        .body(not(containsString("<img src=x onerror=alert(1)>")));
  }
}
