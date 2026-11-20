package dev.omatheusmesmo.qlawkus.tool.shell;

/** A bounded unified diff, using common prefix/suffix lines rather than a quadratic diff matrix. */
final class FileEditDiff {

    private static final String TRUNCATED = "\n[diff preview truncated]\n";

    private FileEditDiff() {
    }

    static String render(String path, String before, String after, int maxChars) {
        String oldText = before;
        String newText = after;
        if (oldText.equals(newText)) {
            return "";
        }
        int prefix = 0;
        int shortest = Math.min(oldText.length(), newText.length());
        while (prefix < shortest && oldText.charAt(prefix) == newText.charAt(prefix)) {
            prefix++;
        }
        int changeStart = lineStart(oldText, prefix);
        int suffix = 0;
        while (suffix < shortest - prefix
                && oldText.charAt(oldText.length() - 1 - suffix) == newText.charAt(newText.length() - 1 - suffix)) {
            suffix++;
        }
        int oldSuffix = nextLine(oldText, oldText.length() - suffix);
        int newSuffix = nextLine(newText, newText.length() - suffix);
        int contextStart = changeStart;
        for (int i = 0; i < 3 && contextStart > 0; i++) {
            contextStart = lineStart(oldText, contextStart - 1);
        }
        int oldEnd = followingLines(oldText, oldSuffix, 3);
        int newEnd = followingLines(newText, newSuffix, 3);
        int oldCount = lines(oldText, contextStart, oldEnd);
        int newCount = lines(newText, contextStart, newEnd);
        int line = lines(oldText, 0, contextStart) + 1;
        Output out = new Output(maxChars);
        String name = path.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        out.append("--- a/" + name + "\n+++ b/" + name + "\n");
        out.append("@@ -" + (oldCount == 0 ? line - 1 : line) + "," + oldCount
                + " +" + (newCount == 0 ? line - 1 : line) + "," + newCount + " @@\n");
        out.lines(' ', oldText, contextStart, changeStart);
        out.lines('-', oldText, changeStart, oldSuffix);
        out.lines('+', newText, changeStart, newSuffix);
        out.lines(' ', oldText, oldSuffix, oldEnd);
        return out.finish();
    }

    private static int lineStart(String text, int offset) {
        int start = offset;
        while (start > 0 && text.charAt(start - 1) != '\n') {
            start--;
        }
        return start;
    }

    private static int nextLine(String text, int offset) {
        if (offset == 0 || offset == text.length() || text.charAt(offset - 1) == '\n') {
            return offset;
        }
        int end = text.indexOf('\n', offset);
        return end < 0 ? text.length() : end + 1;
    }

    private static int followingLines(String text, int offset, int count) {
        int end = offset;
        for (int i = 0; i < count && end < text.length(); i++) {
            int newline = text.indexOf('\n', end);
            end = newline < 0 ? text.length() : newline + 1;
        }
        return end;
    }

    private static int lines(String text, int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count + (end > start && text.charAt(end - 1) != '\n' ? 1 : 0);
    }

    private static final class Output {
        private final int limit;
        private final StringBuilder content = new StringBuilder();
        private boolean truncated;

        Output(int maxChars) {
            limit = Math.max(0, maxChars - TRUNCATED.length());
        }

        void append(String text) {
            append(text, 0, text.length());
        }

        void append(String text, int start, int end) {
            if (truncated) {
                return;
            }
            int available = limit - content.length();
            int length = end - start;
            if (length > available) {
                int until = start + available;
                if (until > start && Character.isHighSurrogate(text.charAt(until - 1))) {
                    until--;
                }
                content.append(text, start, until);
                truncated = true;
            } else {
                content.append(text, start, end);
            }
        }

        void lines(char prefix, String text, int start, int end) {
            int offset = start;
            while (offset < end && !truncated) {
                int newline = text.indexOf('\n', offset);
                int lineEnd = newline < 0 || newline >= end ? end : newline;
                append(String.valueOf(prefix));
                append(text, offset, lineEnd);
                append("\n");
                if (newline < 0 || newline >= end) {
                    append("\\ No newline at end of file\n");
                }
                offset = lineEnd + 1;
            }
        }

        String finish() {
            return truncated ? content + TRUNCATED : content.toString();
        }
    }
}
