package dev.omatheusmesmo.qlawkus.tool.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FileEditDiffTest {

    @Test
    void keepsOnlyThreeContextLinesAroundTheChangedRegion() {
        assertEquals("--- a/file\n+++ b/file\n@@ -2,7 +2,7 @@\n b\n c\n d\n-e\n+E\n f\n g\n h\n",
                FileEditDiff.render("file", "a\nb\nc\nd\ne\nf\ng\nh\n", "a\nb\nc\nd\nE\nf\ng\nh\n", 12000));
    }

    @Test
    void describesMissingTerminalNewlinesAndDeletion() {
        assertEquals("--- a/file\n+++ b/file\n@@ -1,2 +1,1 @@\n first\n-last\n\\ No newline at end of file\n",
                FileEditDiff.render("file", "first\nlast", "first\n", 12000));
    }

    @Test
    void retainsCarriageReturnsInChangedLines() {
        assertEquals("--- a/file\n+++ b/file\n@@ -1,1 +1,1 @@\n-old\r\n+new\r\n",
                FileEditDiff.render("file", "old\r\n", "new\r\n", 12000));
    }
}
