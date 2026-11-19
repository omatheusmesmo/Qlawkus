package dev.omatheusmesmo.qlawkus.tool.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilePageReaderTest {
    @Test
    void numbersBlankLinesAndNormalizesAllLineEndings() throws IOException {
        String page = read("one\r\n\r\nthree\rfour\n", 2, 2, 2000, 1000);
        assertEquals("2|\n3|three\n[TRUNCATED — continue with offset=4 and limit=2]", page);
        assertEquals("4|four\n", read("one\r\n\r\nthree\rfour\n", 4, 2, 2000, 1000));
    }

    @Test
    void truncatesALongLineAndStillReturnsTheFollowingLine() throws IOException {
        String page = read("x".repeat(100_000) + "\nnext\n", 1, 2, 4, 1000);
        assertEquals("1|xxxx [LINE TRUNCATED]\n2|next\n", page);
    }

    @Test
    void outputBudgetContinuesAtTheFirstUnreturnedLine() throws IOException {
        String source = "one\ntwo\nthree\n";
        assertEquals("1|one\n2|two\n[TRUNCATED — continue with offset=3 and limit=10]",
                read(source, 1, 10, 2000, 12));
        assertEquals("3|three\n", read(source, 3, 10, 2000, 12));
    }

    @Test
    void marksAFirstLineThatCannotFitTheOutputBudget() throws IOException {
        assertEquals("1|abcd [LINE TRUNCATED — read-size limit]\n[TRUNCATED — continue with offset=2 and limit=2]",
                read("abcdefghijkl\nnext\n", 1, 2, 2000, 6));
    }

    @Test
    void reportsEofWithoutInventingAPhantomTrailingLine() throws IOException {
        assertEquals("1|one\n", read("one\n", 1, 1, 2000, 1000));
        assertEquals("[EOF — no line at offset 2]", read("one\n", 2, 10, 2000, 1000));
        assertEquals("[EOF — no line at offset 1]", read("", 1, 10, 2000, 1000));
    }

    @Test
    void doesNotSplitAnEmojiAtTheLineLimit() throws IOException {
        assertEquals("1|a [LINE TRUNCATED]\n", read("a😀b\n", 1, 1, 2, 1000));
    }

    @Test
    void outputBudgetCountsUtf8BytesRatherThanCharacters() throws IOException {
        assertEquals("1|açã [LINE TRUNCATED — read-size limit]\n[TRUNCATED — continue with offset=2 and limit=2]",
                read("ação\nnext\n", 1, 2, 2000, 7));
    }

    private static String read(String text, int offset, int limit, int lineLength, long maxChars) throws IOException {
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            return FilePageReader.read(reader, offset, limit, lineLength, maxChars, StandardCharsets.UTF_8);
        }
    }
}
