package dev.omatheusmesmo.qlawkus.it.consoleskillhub;

import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.incompressible;
import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.postUpload;
import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.skillMd;
import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.text;
import static dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.zip;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;

import dev.omatheusmesmo.qlawkus.it.consoleskillhub.PanelSupport.Entry;
import dev.omatheusmesmo.qlawkus.skill.SkillStore;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Uploads through the panel: an archive over the upload limit is refused as over a cap before anything is
 * read, an archive within it is listed and a chosen skill installed, and a selection above the per-call cap
 * is refused before anything is written.
 */
@QuarkusTest
class UploadLimitTest {

  @Inject
  SkillStore skillStore;

  @Test
  void anArchiveOverTheUploadLimitIsRefusedAsOverACap() throws Exception {
    byte[] archive = zip(text("bundle/oversized/SKILL.md", skillMd("oversized-upload", "Too big")),
        new Entry("bundle/oversized/assets/blob.bin", incompressible(6000)));

    postUpload("install/upload/list", "bundle.zip", archive, null)
        .then().statusCode(400)
        .body(containsString("\"kind\":\"limit\""))
        .body(containsString("4096"));
  }

  @Test
  void anArchiveWithinTheLimitIsListedAndTheChosenSkillIsInstalled() throws Exception {
    byte[] archive = zip(text("bundle/upload-triage/SKILL.md", skillMd("upload-triage", "Triage")),
        text("bundle/upload-tdd/SKILL.md", skillMd("upload-tdd", "Test first")));

    postUpload("install/upload/list", "bundle.zip", archive, null)
        .then().statusCode(200)
        .body(containsString("upload-triage"))
        .body(containsString("upload-tdd"));

    postUpload("install/upload/selection", "bundle.zip", archive, "upload-triage")
        .then().statusCode(200)
        .body(containsString("upload-triage"));

    skillStore.get("upload-triage").orElseThrow();
    assertTrue(skillStore.get("upload-tdd").isEmpty(),
        "only the chosen skill is installed");
  }

  @Test
  void aSelectionAboveThePerCallCapIsRefusedBeforeAnythingIsWritten() throws Exception {
    byte[] archive = zip(text("bundle/cap-one/SKILL.md", skillMd("cap-one", "One")),
        text("bundle/cap-two/SKILL.md", skillMd("cap-two", "Two")),
        text("bundle/cap-three/SKILL.md", skillMd("cap-three", "Three")));

    postUpload("install/upload/selection", "bundle.zip", archive, "all")
        .then().statusCode(400)
        .body(containsString("\"kind\":\"limit\""))
        .body(containsString("at most 2"));

    assertTrue(skillStore.get("cap-one").isEmpty(), "nothing is written");
  }
}
