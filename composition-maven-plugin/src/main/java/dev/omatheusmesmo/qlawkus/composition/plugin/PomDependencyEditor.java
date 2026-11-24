package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

import com.ctc.wstx.stax.WstxInputFactory;
import org.apache.maven.model.Dependency;
import org.codehaus.stax2.XMLStreamReader2;

/** Applies the Maven model's dependency changes at XML source offsets. */
final class PomDependencyEditor {

    private record Span(int start, int end) {
    }

    private record Block(int start, int openingEnd, int closingStart, String name, boolean empty,
            List<Span> entries) {
    }

    private PomDependencyEditor() {
    }

    static String edit(String source, List<Dependency> before, List<Dependency> after) throws XMLStreamException {
        Set<Dependency> original = Collections.newSetFromMap(new IdentityHashMap<>());
        original.addAll(before);
        Set<Dependency> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        retained.addAll(after);
        List<Dependency> additions = after.stream().filter(dependency -> !original.contains(dependency)).toList();

        WstxInputFactory factory = new WstxInputFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        var xml = (XMLStreamReader2) factory.createXMLStreamReader(new StringReader(source));
        try {
            return edit(source, xml, before, retained, additions);
        } finally {
            xml.close();
        }
    }

    private static String edit(String source, XMLStreamReader2 xml, List<Dependency> before, Set<Dependency> retained,
            List<Dependency> additions) throws XMLStreamException {
        String lineEnding = lineEnding(source);
        String projectIndent = "  ";
        while (xml.hasNext()) {
            xml.next();
            if (xml.isStartElement() && xml.getDepth() == 2) {
                String indent = indentation(source, start(xml));
                if (!indent.isEmpty()) {
                    projectIndent = indent;
                }
                if (xml.getLocalName().equals("dependencies")) {
                    return updateBlock(source, readBlock(xml), before, retained, additions, projectIndent, lineEnding);
                }
            }
            if (xml.isEndElement() && xml.getDepth() == 1) {
                String prefix = prefix(xml.getPrefixedName());
                String name = prefix + "dependencies";
                var content = new StringBuilder(projectIndent).append('<').append(name).append('>').append(lineEnding);
                appendDependencies(content, additions, prefix, projectIndent.repeat(2), projectIndent, lineEnding);
                content.append(projectIndent).append("</").append(name).append('>');
                var result = new StringBuilder(source);
                insert(result, source, start(xml), content.toString(), "", lineEnding);
                return result.toString();
            }
        }
        throw new XMLStreamException("No project element found");
    }

    private static Block readBlock(XMLStreamReader2 xml) throws XMLStreamException {
        int start = start(xml);
        int openingEnd = end(xml);
        String name = xml.getPrefixedName();
        boolean empty = xml.isEmptyElement();
        List<Span> entries = new ArrayList<>();
        while (xml.next() != XMLStreamReader2.END_ELEMENT || xml.getDepth() != 2) {
            if (xml.isStartElement() && xml.getDepth() == 3 && xml.getLocalName().equals("dependency")) {
                int entryStart = start(xml);
                xml.skipElement();
                entries.add(new Span(entryStart, end(xml)));
            }
        }
        return new Block(start, openingEnd, start(xml), name, empty, entries);
    }

    private static String updateBlock(String source, Block block, List<Dependency> before, Set<Dependency> retained,
            List<Dependency> additions, String projectIndent, String lineEnding) throws XMLStreamException {
        if (block.entries().size() != before.size()) {
            throw new XMLStreamException("Maven's direct dependencies do not match the project dependency elements");
        }

        var result = new StringBuilder(source);
        if (!additions.isEmpty()) {
            String blockIndent = indentation(source, block.start());
            String entryIndent = block.entries().isEmpty()
                    ? "" : indentation(source, block.entries().getFirst().start());
            if (entryIndent.length() <= blockIndent.length()) {
                entryIndent = blockIndent + projectIndent;
            }
            var content = new StringBuilder();
            appendDependencies(content, additions, prefix(block.name()), entryIndent, projectIndent, lineEnding);
            if (block.empty()) {
                int slash = block.openingEnd() - 2;
                result.replace(slash, block.openingEnd(),
                        ">" + lineEnding + content + blockIndent + "</" + block.name() + ">");
            } else {
                content.setLength(content.length() - lineEnding.length());
                insert(result, source, block.closingStart(), content.toString(), blockIndent, lineEnding);
            }
        }

        for (int index = before.size() - 1; index >= 0; index--) {
            if (!retained.contains(before.get(index))) {
                Span range = wholeLineIfPossible(source, block.entries().get(index));
                result.delete(range.start(), range.end());
            }
        }
        return result.toString();
    }

    private static void appendDependencies(StringBuilder target, List<Dependency> dependencies, String prefix,
            String indent, String unit, String newline) {
        for (Dependency dependency : dependencies) {
            target.append(indent).append('<').append(prefix).append("dependency>").append(newline);
            appendValue(target, prefix + "groupId", dependency.getGroupId(), indent + unit, newline);
            appendValue(target, prefix + "artifactId", dependency.getArtifactId(), indent + unit, newline);
            if (dependency.getVersion() != null) {
                appendValue(target, prefix + "version", dependency.getVersion(), indent + unit, newline);
            }
            target.append(indent).append("</").append(prefix).append("dependency>").append(newline);
        }
    }

    private static void appendValue(StringBuilder target, String name, String value, String indent, String newline) {
        target.append(indent).append('<').append(name).append('>')
                .append(value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                .append("</").append(name).append('>').append(newline);
    }

    private static void insert(StringBuilder result, String source, int closingTag, String content,
            String closingIndent, String newline) {
        int line = lineStart(source, closingTag);
        if (source.substring(line, closingTag).isBlank()) {
            result.insert(line, content + newline);
        } else {
            result.insert(closingTag, newline + content + newline + closingIndent);
        }
    }

    private static Span wholeLineIfPossible(String source, Span span) {
        int first = lineStart(source, span.start());
        int last = span.end();
        while (last < source.length() && (source.charAt(last) == ' ' || source.charAt(last) == '\t')) {
            last++;
        }
        if (!source.substring(first, span.start()).isBlank()) {
            return span;
        }
        if (source.startsWith("\r\n", last)) {
            return new Span(first, last + 2);
        }
        if (source.startsWith("\n", last) || source.startsWith("\r", last)) {
            return new Span(first, last + 1);
        }
        return span;
    }

    private static int lineStart(String source, int offset) {
        return 1 + Math.max(source.lastIndexOf('\n', offset - 1), source.lastIndexOf('\r', offset - 1));
    }

    private static String indentation(String source, int offset) {
        String indent = source.substring(lineStart(source, offset), offset);
        return indent.isBlank() ? indent : "";
    }

    private static String lineEnding(String source) {
        int first = source.indexOf('\r');
        int lf = source.indexOf('\n');
        return first >= 0 && (lf < 0 || first < lf) ? (source.startsWith("\r\n", first) ? "\r\n" : "\r") : "\n";
    }

    private static String prefix(String name) {
        return name.substring(0, name.indexOf(':') + 1);
    }

    private static int start(XMLStreamReader2 xml) {
        return Math.toIntExact(xml.getLocationInfo().getStartingCharOffset());
    }

    private static int end(XMLStreamReader2 xml) throws XMLStreamException {
        return Math.toIntExact(xml.getLocationInfo().getEndingCharOffset());
    }
}
