package dev.omatheusmesmo.qlawkus.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolProgressTest {

  @Test
  void namesAreReadIgnoringCaseAndABlankOneIsThePreviewDefault() {
    assertEquals(ToolProgress.VERBOSE, ToolProgress.parse(" Verbose "));
    assertEquals(ToolProgress.PREVIEW, ToolProgress.parse(null));
    assertEquals(ToolProgress.PREVIEW, ToolProgress.parse(" "));
    assertEquals("status", ToolProgress.STATUS.wireName());
  }

  @Test
  void anUnknownNameIsRejectedRatherThanTreatedAsAMode() {
    assertThrows(IllegalArgumentException.class, () -> ToolProgress.parse("loud"));
  }

  @Test
  void offAndStatusNeverShowArguments() {
    assertNull(ToolProgress.OFF.detail("{\"q\":\"x\"}"));
    assertNull(ToolProgress.STATUS.detail("{\"q\":\"x\"}"));
    assertFalse(ToolProgress.OFF.showsTools());
    assertTrue(ToolProgress.STATUS.showsTools());
  }

  @Test
  void previewCapsAtFortyCharactersAndVerboseAtThreeHundred() {
    String arguments = "a".repeat(500);

    assertEquals("a".repeat(40) + "...", ToolProgress.PREVIEW.detail(arguments));
    assertEquals("a".repeat(300) + "...", ToolProgress.VERBOSE.detail(arguments));
    assertEquals("short", ToolProgress.PREVIEW.detail("short"));
  }

  @Test
  void truncationNeverSplitsASurrogatePair() {
    String arguments = "a".repeat(39) + "😀" + "tail";

    String detail = ToolProgress.PREVIEW.detail(arguments);

    assertEquals("a".repeat(39) + "...", detail);
  }
}
