package dev.omatheusmesmo.qlawkus.tool.shell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses the Qlawkus patch envelope (Begin Patch / End Patch). */
final class StructuredPatchParser {

    private static final String BEGIN = "Begin Patch";
    private static final String END = "End Patch";

    private StructuredPatchParser() {
    }

    static StructuredPatch parse(String patch) {
        if (patch == null || patch.isBlank()) {
            throw new StructuredPatchException("Patch body must not be empty.");
        }
        List<String> lines = patch.lines().toList();
        int start = 0;
        while (start < lines.size() && lines.get(start).isBlank()) {
            start++;
        }
        int end = lines.size() - 1;
        while (end >= start && lines.get(end).isBlank()) {
            end--;
        }
        if (end < start || !lines.get(start).trim().equals(BEGIN) || !lines.get(end).trim().equals(END)) {
            throw new StructuredPatchException("Patch must start with \"Begin Patch\" and end with \"End Patch\".");
        }
        List<StructuredPatch.PatchOperation> operations = new ArrayList<>();
        int index = start + 1;
        while (index < end) {
            while (index < end && lines.get(index).isBlank()) {
                index++;
            }
            if (index >= end) {
                break;
            }
            String header = lines.get(index).trim();
            if (header.regionMatches(true, 0, "Add File:", 0, "Add File:".length())) {
                String path = header.substring("Add File:".length()).trim();
                index++;
                List<String> content = new ArrayList<>();
                while (index < end && !isOperationHeader(lines.get(index))) {
                    content.add(lines.get(index));
                    index++;
                }
                operations.add(new StructuredPatch.PatchOperation.Add(path, String.join("\n", content)));
            } else if (header.regionMatches(true, 0, "Update File:", 0, "Update File:".length())) {
                String path = header.substring("Update File:".length()).trim();
                index++;
                index = skipBlank(lines, index, end);
                if (index >= end || !lines.get(index).trim().equals("<<<SEARCH")) {
                    throw new StructuredPatchException("Update File for " + path + " must include <<<SEARCH.");
                }
                index++;
                StringBuilder search = new StringBuilder();
                while (index < end && !lines.get(index).trim().equals("===")) {
                    if (!search.isEmpty()) {
                        search.append('\n');
                    }
                    search.append(lines.get(index));
                    index++;
                }
                if (index >= end) {
                    throw new StructuredPatchException("Update File for " + path + " missing === separator.");
                }
                index++;
                StringBuilder replace = new StringBuilder();
                while (index < end && !lines.get(index).trim().equals(">>>")) {
                    if (!replace.isEmpty()) {
                        replace.append('\n');
                    }
                    replace.append(lines.get(index));
                    index++;
                }
                if (index >= end) {
                    throw new StructuredPatchException("Update File for " + path + " missing >>> terminator.");
                }
                index++;
                operations.add(new StructuredPatch.PatchOperation.Update(path, search.toString(), replace.toString()));
            } else if (header.regionMatches(true, 0, "Delete File:", 0, "Delete File:".length())) {
                String path = header.substring("Delete File:".length()).trim();
                operations.add(new StructuredPatch.PatchOperation.Delete(path));
                index++;
            } else if (header.regionMatches(true, 0, "Move File:", 0, "Move File:".length())) {
                String from = header.substring("Move File:".length()).trim();
                index++;
                index = skipBlank(lines, index, end);
                if (index >= end) {
                    throw new StructuredPatchException("Move File for " + from + " must include a To: line.");
                }
                String toLine = lines.get(index).trim();
                if (!toLine.regionMatches(true, 0, "To:", 0, "To:".length())) {
                    throw new StructuredPatchException("Move File for " + from + " must be followed by To: <path>.");
                }
                String to = toLine.substring("To:".length()).trim();
                operations.add(new StructuredPatch.PatchOperation.Move(from, to));
                index++;
            } else {
                throw new StructuredPatchException("Unknown patch operation: " + header);
            }
        }
        if (operations.isEmpty()) {
            throw new StructuredPatchException("Patch contains no file operations.");
        }
        if (operations.size() > 32) {
            throw new StructuredPatchException("Patch exceeds the limit of 32 file operations.");
        }
        return new StructuredPatch(List.copyOf(operations));
    }

    private static int skipBlank(List<String> lines, int index, int end) {
        while (index < end && lines.get(index).isBlank()) {
            index++;
        }
        return index;
    }

    private static boolean isOperationHeader(String line) {
        String trimmed = line.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("add file:")
                || lower.startsWith("update file:")
                || lower.startsWith("delete file:")
                || lower.startsWith("move file:");
    }

    static final class StructuredPatchException extends RuntimeException {
        StructuredPatchException(String message) {
            super(message);
        }
    }
}
