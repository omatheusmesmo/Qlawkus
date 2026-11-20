package dev.omatheusmesmo.qlawkus.tool.shell;

import dev.omatheusmesmo.qlawkus.dto.FileEdit;

import java.util.List;

/** Applies a complete edit batch in memory before the caller is allowed to commit it. */
final class ExactFileEdits {

    private ExactFileEdits() {
    }

    static Result apply(String original, List<FileEdit> edits, boolean replaceAll, int maxChars) {
        String newline = uniformNewline(original);
        String content = original;
        int replacements = 0;
        for (int i = 0; i < edits.size(); i++) {
            FileEdit edit = edits.get(i);
            String oldText = normalize(edit.oldText(), newline);
            String newText = normalize(edit.newText(), newline);
            int first = content.indexOf(oldText);
            if (first < 0) {
                throw new Rejected("no_match", "Edit " + (i + 1)
                        + ": oldText was not found. Re-read the file and copy exact text, including indentation. No edits were written.");
            }
            if (!replaceAll && content.indexOf(oldText, first + 1) >= 0) {
                throw new Rejected("ambiguous", "Edit " + (i + 1)
                        + ": oldText matches more than once. Include more context or use replaceAll. No edits were written.");
            }
            int count = 1;
            if (replaceAll) {
                int next = first + oldText.length();
                while ((next = content.indexOf(oldText, next)) >= 0) {
                    count++;
                    next += oldText.length();
                }
            }
            long newLength = content.length() + (long) count * (newText.length() - oldText.length());
            if (newLength > maxChars) {
                throw new Rejected("limit", "Edited content exceeds the write-size limit. No edits were written.");
            }
            content = replaceAll ? content.replace(oldText, newText)
                    : content.substring(0, first) + newText + content.substring(first + oldText.length());
            replacements += count;
        }
        return new Result(content, replacements);
    }

    private static String normalize(String text, String newline) {
        if (newline == null) {
            return text;
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').replace("\n", newline);
    }

    /** Mixed line endings stay exact; a uniform file controls only the supplied edit arguments. */
    private static String uniformNewline(String text) {
        String newline = null;
        for (int i = 0; i < text.length(); i++) {
            String found;
            if (text.charAt(i) == '\r') {
                found = i + 1 < text.length() && text.charAt(i + 1) == '\n' ? "\r\n" : "\r";
                if (found.length() == 2) {
                    i++;
                }
            } else if (text.charAt(i) == '\n') {
                found = "\n";
            } else {
                continue;
            }
            if (newline != null && !newline.equals(found)) {
                return null;
            }
            newline = found;
        }
        return newline == null ? "\n" : newline;
    }

    record Result(String content, int replacements) {
    }

    static final class Rejected extends IllegalArgumentException {
        final String outcome;

        Rejected(String outcome, String message) {
            super(message);
            this.outcome = outcome;
        }
    }
}
