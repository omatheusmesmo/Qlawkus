package dev.omatheusmesmo.qlawkus.it.console;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The chat page's attachment control is a shared-component button that opens a hidden file input, with a
 * list of the chosen files the owner can prune, instead of the browser's raw file input and an unstyled
 * label sitting in the message row. The look is checked in a browser by `browser/attach-check.mjs`.
 */
@QuarkusTest
class ChatAttachButtonTest {

  private static String page() {
    return given().auth().preemptive().basic("qlawkus", "qlawkus-test")
        .get("/console/chat").then().statusCode(200).extract().asString();
  }

  private static String asset(String name) {
    return given().get("/console/" + name).then().statusCode(200).extract().asString();
  }

  private static String tag(String html, String id) {
    Matcher match = Pattern.compile("<[a-z]+\\b[^>]*\\bid=\"" + id + "\"[^>]*>", Pattern.DOTALL).matcher(html);
    assertTrue(match.find(), "no element with id " + id);
    return match.group();
  }

  @Test
  void attachIsASharedButtonWithAnAccessibleNameThatOpensTheFileInput() {
    String attach = tag(page(), "chat-attach");

    assertThat(attach, containsString("type=\"button\""));
    assertThat(attach, containsString("class=\"btn btn-secondary chat-attach\""));
    assertThat(attach, containsString("aria-label=\"Attach files\""));
    assertThat(attach, containsString("aria-controls=\"chat-attachments\""));
  }

  @Test
  void theNativeFileInputIsHiddenSoOnlyTheButtonIsShown() {
    String input = tag(page(), "chat-attachments");

    assertThat(input, containsString("type=\"file\""));
    assertThat(input, containsString("multiple"));
    assertThat(input, containsString("hidden"));
    assertThat(input, containsString("accept=\".pdf,.png,.jpg,.jpeg\""));
  }

  @Test
  void theChosenFilesGetTheirOwnLabelledList() {
    String list = tag(page(), "chat-files");

    assertThat(list, containsString("aria-label=\"Attached files\""));
    assertThat(list, containsString("hidden"));
  }

  @Test
  void theLimitsAreStatedOnceOnTheFormForTheScriptAndForScreenReaders() {
    String html = page();

    assertThat(tag(html, "chat-form"), containsString("data-max-files=\"3\""));
    assertThat(tag(html, "chat-form"), containsString("data-max-bytes=\"2097152\""));
    assertThat(tag(html, "chat-attach"), containsString("aria-describedby=\"chat-attach-hint\""));
    assertThat(html, containsString("PDF, PNG or JPEG, up to 3 files of 2 MiB each"));
    assertThat(html, not(containsString(">Attach PDF, PNG or JPEG</label>")));
  }

  @Test
  void messageAndSendUseTheSharedControlClasses() {
    String html = page();

    assertThat(tag(html, "chat-input"), containsString("class=\"textarea chat-input\""));
    assertThat(tag(html, "chat-send"), containsString("class=\"btn btn-primary\""));
  }

  @Test
  void theStylesheetNoLongerStylesEveryButtonInTheFormAndKeepsPhoneTargetsBigEnough() {
    String css = asset("app.css");

    assertFalse(css.contains(".chat-form button {"), "the form-wide button rule is gone");
    assertFalse(css.contains(".chat-form textarea {"), "the textarea uses the shared .textarea");
    assertTrue(css.contains(".chat-compose"), "the composer row has its own layout");
    assertTrue(css.contains(".chat-file-remove"), "a chosen file can be removed");
    Matcher phone = Pattern.compile("@media \\(max-width: 800px\\) \\{(.*?)\\n\\}", Pattern.DOTALL).matcher(css);
    boolean sized = false;
    while (phone.find()) {
      sized |= phone.group(1).contains(".chat-file-remove") && phone.group(1).contains("44px");
    }
    assertTrue(sized, "on a phone the remove target is 44px");
    Matcher narrow = Pattern.compile("@media \\(max-width: 380px\\) \\{(.*?)\\n\\}", Pattern.DOTALL).matcher(css);
    assertTrue(narrow.find() && narrow.group(1).contains(".chat-compose") && narrow.group(1).contains("flex-wrap: wrap"),
        "on a 320px screen the message box takes its own row");
  }

  @Test
  void theScriptChecksFilesWhenTheyAreChosenAndNeverWritesMarkup() {
    String script = asset("chat.js");

    assertThat(script, containsString("chat-attach"));
    assertThat(script, containsString("data-max-bytes"));
    assertThat(script, containsString("'Remove '"));
    assertFalse(script.contains("innerHTML"), "model and file names only reach the page as text");
  }
}
