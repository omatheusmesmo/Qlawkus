package dev.omatheusmesmo.qlawkus.it.consoleskillhub;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * The install page sits behind the console's credentials, renders the three install sources and the per-call
 * cap, is linked from the skills page, and its script inserts text only.
 */
@QuarkusTest
class InstallPanelPageTest {

  @Test
  void theInstallPageRequiresCredentials() {
    given().when().get("/console/skills/install").then().statusCode(401);
  }

  @Test
  void theInstallPageRendersTheLinkTextAndUploadForms() {
    given().auth().basic(PanelSupport.USER, PanelSupport.PASSWORD)
        .when().get("/console/skills/install")
        .then().statusCode(200)
        .body(containsString("id=\"install-link-form\""))
        .body(containsString("id=\"install-text-form\""))
        .body(containsString("id=\"install-upload-form\""))
        .body(containsString("type=\"file\""))
        .body(containsString("data-max-installs=\"2\""))
        .body(containsString("data-upload-limit=\"4096\""))
        .body(containsString("/console/skill-install.js"));
  }

  @Test
  void theSkillsPageLinksTheInstallPanelBecauseTheExtensionIsPresent() {
    given().auth().basic(PanelSupport.USER, PanelSupport.PASSWORD)
        .when().get("/console/skills")
        .then().statusCode(200)
        .body(containsString("href=\"/console/skills/install\""));
  }

  @Test
  void theScriptInsertsTextOnlyNeverMarkup() {
    given().when().get("/console/skill-install.js")
        .then().statusCode(200)
        .body(containsString("textContent"))
        .body(not(containsString("innerHTML")));
  }
}
