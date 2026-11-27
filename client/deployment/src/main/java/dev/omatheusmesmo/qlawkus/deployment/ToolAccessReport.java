package dev.omatheusmesmo.qlawkus.deployment;

import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessEntry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * The build's account of how every tool was classified, one tab separated line per tool, so a change
 * to a class (or a tool that fell back to the most restrictive one) shows in a diff of two builds.
 */
public final class ToolAccessReport {

    public static final String FILE_NAME = "qlawkus-tool-access.tsv";
    private static final String HEADER = "tool\tclass\tsource\torigin";

    private ToolAccessReport() {
    }

    public static String render(List<ToolAccessEntry> entries) {
        StringBuilder report = new StringBuilder(HEADER).append('\n');
        entries.stream().sorted(Comparator.comparing(ToolAccessEntry::tool)).forEach(entry -> report
                .append(entry.tool()).append('\t')
                .append(entry.accessClass()).append('\t')
                .append(entry.source()).append('\t')
                .append(entry.origin()).append('\n'));
        return report.toString();
    }

    public static Path write(Path directory, List<ToolAccessEntry> entries) {
        try {
            Files.createDirectories(directory);
            return Files.writeString(directory.resolve(FILE_NAME), render(entries));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the tool access report to " + directory, e);
        }
    }
}
