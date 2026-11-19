package dev.omatheusmesmo.qlawkus.tool.shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;

/** Reads numbered pages without allocating the unreturned tail of a long line. */
final class FilePageReader {
    private FilePageReader() {
    }

    static String read(BufferedReader reader, int offset, int limit, int maxLineLength, long maxBytes, Charset charset)
            throws IOException {
        for (int line = 1; line < offset; line++) {
            if (readLine(reader, 0) == null) {
                return "[EOF — no line at offset " + offset + "]";
            }
        }

        StringBuilder output = new StringBuilder();
        long usedBytes = 0;
        long nextLine = offset;
        boolean more = false;
        for (int count = 0; count < limit; count++) {
            Line line = readLine(reader, maxLineLength);
            if (line == null) {
                break;
            }
            String prefix = nextLine + "|";
            String rendered = prefix + line.text()
                    + (line.truncated() ? " [LINE TRUNCATED]" : "") + "\n";
            int renderedBytes = rendered.getBytes(charset).length;
            if (renderedBytes > maxBytes - usedBytes) {
                if (!output.isEmpty()) {
                    more = true;
                    break;
                }
                int prefixBytes = prefix.getBytes(charset).length;
                if (maxBytes <= prefixBytes) {
                    throw new IOException("Read-size limit is too small for a numbered line");
                }
                output.append(prefix).append(fitBytes(line.text(), maxBytes - prefixBytes, charset))
                        .append(" [LINE TRUNCATED — read-size limit]\n");
                nextLine++;
                more = reader.read() != -1;
                break;
            }
            output.append(rendered);
            usedBytes += renderedBytes;
            nextLine++;
            if (count == limit - 1) {
                more = reader.read() != -1;
            }
        }

        if (output.isEmpty() && !more) {
            return "[EOF — no line at offset " + offset + "]";
        }
        if (more) {
            output.append("[TRUNCATED — continue with offset=").append(nextLine)
                    .append(" and limit=").append(limit).append("]");
        }
        return output.toString();
    }

    private static Line readLine(BufferedReader reader, int maxLength) throws IOException {
        int current = reader.read();
        if (current == -1) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        boolean truncated = false;
        while (current != -1 && current != '\n' && current != '\r') {
            if (text.length() < maxLength) {
                text.append((char) current);
            } else {
                truncated = true;
            }
            current = reader.read();
        }
        if (current == '\r') {
            reader.mark(1);
            if (reader.read() != '\n') {
                reader.reset();
            }
        }
        return new Line(withoutSplitSurrogate(text.toString()), truncated);
    }

    private static String withoutSplitSurrogate(String text) {
        if (!text.isEmpty() && Character.isHighSurrogate(text.charAt(text.length() - 1))) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String fitBytes(String text, long budget, Charset charset) {
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (text.substring(0, middle).getBytes(charset).length <= budget) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return withoutSplitSurrogate(text.substring(0, low));
    }

    private record Line(String text, boolean truncated) {
    }
}
