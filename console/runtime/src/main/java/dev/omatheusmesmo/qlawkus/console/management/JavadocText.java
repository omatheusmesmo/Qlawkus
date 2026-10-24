package dev.omatheusmesmo.qlawkus.console.management;

import java.util.Set;

/**
 * Renders the Javadoc markup of a config description as safe HTML. An inline code tag becomes a code
 * element, an inline link tag a code element holding the simple name of its target (or its label), and
 * an inline literal tag plain text. Every other character, including an unknown or unclosed tag, is
 * HTML-escaped and kept, so the output never carries markup that was not produced here.
 */
public final class JavadocText {

    private static final String TAG_OPEN = "{@";
    private static final Set<String> KNOWN_TAGS = Set.of("code", "link", "linkplain", "literal");

    private JavadocText() {
    }

    public static String toHtml(String javadoc) {
        if (javadoc == null || javadoc.isBlank()) {
            return "";
        }
        StringBuilder html = new StringBuilder(javadoc.length() + 16);
        int index = 0;
        while (index < javadoc.length()) {
            int consumed = javadoc.startsWith(TAG_OPEN, index) ? appendTag(javadoc, index, html) : 0;
            if (consumed > 0) {
                index += consumed;
            } else {
                appendEscaped(html, javadoc.charAt(index));
                index++;
            }
        }
        return html.toString();
    }

    private static int appendTag(String javadoc, int start, StringBuilder html) {
        int nameStart = start + TAG_OPEN.length();
        int nameEnd = nameStart;
        while (nameEnd < javadoc.length() && Character.isLetter(javadoc.charAt(nameEnd))) {
            nameEnd++;
        }
        String name = javadoc.substring(nameStart, nameEnd);
        int close = matchingBrace(javadoc, start);
        boolean separated = nameEnd < javadoc.length() && Character.isWhitespace(javadoc.charAt(nameEnd));
        if (!KNOWN_TAGS.contains(name) || close < 0 || !separated) {
            return 0;
        }
        String body = javadoc.substring(nameEnd, close).strip();
        switch (name) {
            case "code" -> html.append("<code>").append(escape(body)).append("</code>");
            case "literal" -> html.append(escape(body));
            default -> html.append("<code>").append(escape(linkText(body))).append("</code>");
        }
        return close + 1 - start;
    }

    private static int matchingBrace(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String linkText(String body) {
        int space = body.indexOf(' ');
        if (space > 0) {
            return body.substring(space + 1).strip();
        }
        String target = body.startsWith("#") ? body.substring(1) : body;
        int member = target.indexOf('#');
        String type = member < 0 ? target : target.substring(0, member);
        String simpleType = type.substring(type.lastIndexOf('.') + 1);
        return member < 0 ? simpleType : simpleType + target.substring(member);
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            appendEscaped(out, text.charAt(i));
        }
        return out.toString();
    }

    private static void appendEscaped(StringBuilder out, char c) {
        switch (c) {
            case '&' -> out.append("&amp;");
            case '<' -> out.append("&lt;");
            case '>' -> out.append("&gt;");
            case '"' -> out.append("&quot;");
            case '\'' -> out.append("&#39;");
            default -> out.append(c);
        }
    }
}
