package dev.omatheusmesmo.qlawkus.tool.shell;

/** Reserves space for the bounded limit/skip notices, rather than silently dropping those notices. */
final class SearchOutput {
    private final StringBuilder text = new StringBuilder();
    private final int contentLimit;

    SearchOutput(int configuredLimit) {
        contentLimit = Math.clamp(configuredLimit, 1024, 128000) - 512;
    }

    boolean append(String line) {
        if (text.length() + line.length() + 1 > contentLimit) {
            return false;
        }
        if (!text.isEmpty()) {
            text.append('\n');
        }
        text.append(line);
        return true;
    }

    String finish(WorkspaceSearch search) {
        String notice = search.notice();
        String body = text.toString();
        if (body.isEmpty()) {
            body = notice.isEmpty() ? "No matches found." : "No matches returned.";
        }
        return body + notice;
    }
}
