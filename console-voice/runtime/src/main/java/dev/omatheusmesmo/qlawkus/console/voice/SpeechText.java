package dev.omatheusmesmo.qlawkus.console.voice;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/**
 * Speech-only rendering of streamed Markdown. Complete blocks use the upstream CommonMark parser,
 * so delimiters and fenced code may cross model chunks. A still-open block waits for completion,
 * which can delay the first audio of a single long paragraph. The original screen text is untouched.
 */
final class SpeechText {
    static final int MAX_SOURCE_CHARS = 65_536;

    private static final Pattern BLANK_LINE = Pattern.compile("\\r?\\n[\\t ]*\\r?\\n");
    private static final Parser MARKDOWN = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS)
            .extensions(List.of(StrikethroughExtension.create(), TablesExtension.create())).build();
    private final StringBuilder pending = new StringBuilder();
    private int received;
    private boolean truncated;

    String accept(String chunk) {
        int count = Math.min(chunk.length(), MAX_SOURCE_CHARS - received);
        pending.append(chunk, 0, count);
        received += count;
        truncated |= count < chunk.length();
        if (count == 0) return "";
        if (chunk.indexOf('\n') < 0) return "";
        Matcher boundaries = BLANK_LINE.matcher(pending);
        int end = 0;
        while (boundaries.find()) end = boundaries.end();
        if (end == 0) return "";

        Node document = MARKDOWN.parse(pending.substring(0, end));
        Node last = document.getLastChild();
        // Blank lines can occur inside these blocks. Keep the final block until another block or
        // end-of-turn establishes its boundary, rather than narrating a later code fragment as prose.
        if (last instanceof FencedCodeBlock || last instanceof IndentedCodeBlock || last instanceof HtmlBlock
                || last instanceof TableBlock) {
            end = last.getSourceSpans().getFirst().getInputIndex();
            last.unlink();
        }
        pending.delete(0, end);
        return render(document);
    }

    String flush() {
        Node document = MARKDOWN.parse(pending.toString());
        pending.setLength(0);
        return render(document);
    }

    boolean truncated() {
        return truncated;
    }

    private static String render(Node document) {
        PlainSpeech visitor = new PlainSpeech();
        document.accept(visitor);
        StringBuilder clean = new StringBuilder();
        String text = visitor.text.toString();
        boolean space = false;
        for (int offset = 0; offset < text.length();) {
            int point = text.codePointAt(offset);
            offset += Character.charCount(point);
            int after = offset < text.length() ? text.codePointAt(offset) : -1;
            if (Character.isEmojiPresentation(point) || Character.isExtendedPictographic(point)
                    || Character.isEmojiModifier(point) || point == 0x200d || point == 0xfe0f
                    || point == 0xfe0e || point == 0x20e3 || Character.getType(point) == Character.SURROGATE) continue;
            if ((point >= '0' && point <= '9' || point == '#' || point == '*')
                    && (after == 0x20e3 || after == 0xfe0f && offset + 1 < text.length()
                    && text.codePointAt(offset + 1) == 0x20e3)) continue;
            if (Character.isWhitespace(point) || Character.isSpaceChar(point)) {
                space = !clean.isEmpty();
            } else {
                if (space) clean.append(' ');
                clean.appendCodePoint(point);
                space = false;
            }
        }
        return clean.toString();
    }

    /** Visits text only: presentation, destinations and code blocks are not useful spoken output. */
    private static final class PlainSpeech extends AbstractVisitor {
        private final StringBuilder text = new StringBuilder();

        @Override public void visit(Text node) { text.append(node.getLiteral()); }
        @Override public void visit(Code node) { text.append(node.getLiteral()); }
        @Override public void visit(SoftLineBreak node) { text.append(' '); }
        @Override public void visit(HardLineBreak node) { text.append(' '); }
        @Override public void visit(Paragraph node) { visitChildren(node); text.append(' '); }
        @Override public void visit(Heading node) { visitChildren(node); text.append(' '); }
        @Override public void visit(FencedCodeBlock node) { }
        @Override public void visit(IndentedCodeBlock node) { }
        @Override public void visit(HtmlBlock node) { }
        @Override public void visit(HtmlInline node) { }
        @Override public void visit(Image node) { }
        @Override public void visit(CustomBlock node) { if (!(node instanceof TableBlock)) visitChildren(node); }
    }
}
