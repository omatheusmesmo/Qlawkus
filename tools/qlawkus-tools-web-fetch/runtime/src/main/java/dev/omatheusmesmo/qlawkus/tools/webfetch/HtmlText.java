package dev.omatheusmesmo.qlawkus.tools.webfetch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Turns an HTML page into the readable text an agent needs: headings, paragraphs, lists, links, code and
 * table cells, without scripts, styles or page chrome. Markdown output keeps the structure as light
 * markup; plain output keeps only the words. Only http and https links survive, so a
 * {@code javascript:} or {@code data:} target never reaches the model.
 */
final class HtmlText {

    record Converted(String title, String body) {
    }

    private static final String NOISE = "script,style,noscript,template,svg,canvas,iframe,object,embed,nav,footer,aside,"
            + "form,button,select,[hidden],[aria-hidden=true]";
    private static final String CONTENT_ROOT = "main,article,[role=main]";

    private HtmlText() {
    }

    static Converted convert(byte[] html, String charset, String baseUri, boolean markdown) {
        Document document = parse(html, charset, baseUri);
        String title = document.title().strip();
        document.select(NOISE).remove();
        Element root = document.selectFirst(CONTENT_ROOT);
        if (root == null) {
            root = document.body();
        }
        Renderer renderer = new Renderer(markdown);
        renderer.children(root);
        return new Converted(title, renderer.result());
    }

    private static Document parse(byte[] html, String charset, String baseUri) {
        try {
            return Jsoup.parse(new ByteArrayInputStream(html), supported(charset), baseUri);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String supported(String charset) {
        try {
            return charset != null && Charset.isSupported(charset) ? charset : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static final class Renderer {

        private final boolean markdown;
        private final StringBuilder out = new StringBuilder();
        private final Deque<int[]> lists = new ArrayDeque<>();

        Renderer(boolean markdown) {
            this.markdown = markdown;
        }

        String result() {
            return out.toString().replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").strip();
        }

        void children(Element element) {
            for (Node child : element.childNodes()) {
                node(child);
            }
        }

        private void node(Node node) {
            if (node instanceof TextNode text) {
                inline(text.text());
            } else if (node instanceof Element element) {
                element(element);
            }
        }

        private void element(Element element) {
            String tag = element.normalName();
            switch (tag) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> heading(element, tag.charAt(1) - '0');
                case "p", "div", "section", "article", "main", "header", "blockquote", "table", "figure",
                        "figcaption", "dl", "tbody", "thead", "details", "summary", "address" -> block(element);
                case "br" -> newline();
                case "hr" -> blank();
                case "ul", "ol" -> list(element, "ol".equals(tag));
                case "li" -> item(element, "- ");
                case "dt", "dd", "tr" -> {
                    newline();
                    children(element);
                    newline();
                }
                case "td", "th" -> {
                    inline(" ");
                    children(element);
                    inline(" ");
                }
                case "a" -> link(element);
                case "b", "strong" -> wrap(element, "**");
                case "i", "em" -> wrap(element, "*");
                case "code", "kbd", "samp" -> wrap(element, "`");
                case "pre" -> pre(element);
                case "img" -> image(element);
                default -> children(element);
            }
        }

        private void heading(Element element, int level) {
            blank();
            if (markdown) {
                out.append("#".repeat(level)).append(' ');
            }
            children(element);
            blank();
        }

        private void block(Element element) {
            blank();
            children(element);
            blank();
        }

        private void list(Element element, boolean ordered) {
            newline();
            int[] counter = {0, ordered ? 1 : 0};
            lists.push(counter);
            for (Element child : element.children()) {
                if ("li".equals(child.normalName())) {
                    counter[0]++;
                    item(child, ordered ? counter[0] + ". " : "- ");
                } else {
                    element(child);
                }
            }
            lists.pop();
            newline();
        }

        private void item(Element element, String marker) {
            newline();
            out.append("  ".repeat(Math.max(0, lists.size() - 1))).append(marker);
            children(element);
            newline();
        }

        private void link(Element element) {
            String href = element.absUrl("href");
            boolean safe = markdown && (href.startsWith("http://") || href.startsWith("https://"));
            int start = out.length();
            if (safe) {
                out.append('[');
            }
            children(element);
            if (safe) {
                if (out.length() - start <= 1) {
                    out.setLength(start);
                } else {
                    out.append("](").append(href.replace(" ", "%20").replace("(", "%28").replace(")", "%29")).append(')');
                }
            }
        }

        private void wrap(Element element, String marker) {
            int start = out.length();
            if (markdown) {
                out.append(marker);
            }
            children(element);
            if (markdown) {
                if (out.length() - start <= marker.length()) {
                    out.setLength(start);
                } else {
                    out.append(marker);
                }
            }
        }

        private void pre(Element element) {
            blank();
            String code = element.wholeText().strip();
            if (markdown) {
                out.append("```\n").append(code).append("\n```");
            } else {
                out.append(code);
            }
            blank();
        }

        private void image(Element element) {
            String alt = element.attr("alt").strip();
            if (!alt.isEmpty()) {
                inline("[image: " + alt + "]");
            }
        }

        private void inline(String text) {
            if (text.isEmpty()) {
                return;
            }
            String collapsed = text.replaceAll("\\s+", " ");
            if (collapsed.startsWith(" ") && (out.isEmpty() || endsWithWhitespace())) {
                collapsed = collapsed.stripLeading();
            }
            out.append(collapsed);
        }

        private boolean endsWithWhitespace() {
            return Character.isWhitespace(out.charAt(out.length() - 1));
        }

        private void newline() {
            if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
        }

        private void blank() {
            if (out.isEmpty()) {
                return;
            }
            newline();
            if (out.length() < 2 || out.charAt(out.length() - 2) != '\n') {
                out.append('\n');
            }
        }
    }
}
