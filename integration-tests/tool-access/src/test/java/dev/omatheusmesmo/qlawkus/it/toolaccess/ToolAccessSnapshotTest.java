package dev.omatheusmesmo.qlawkus.it.toolaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.deployment.ToolAccessScanner;
import dev.omatheusmesmo.qlawkus.tool.access.ToolAccessEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

/**
 * Pins the access class and its source for every tool of every Qlawkus extension. When a tool is
 * added, renamed or reclassified this fails with the two lists to compare; run it with
 * {@code -Dqlawkus.tool-access.update=true} to rewrite {@code src/test/resources/tool-access.snapshot}
 * and review the diff of that file.
 */
class ToolAccessSnapshotTest {

    private static final String SNAPSHOT = "tool-access.snapshot";
    private static final String UPDATE_PROPERTY = "qlawkus.tool-access.update";
    private static final String QLAWKUS_GROUP = "dev.omatheusmesmo:";

    @Test
    void everyToolKeepsItsPinnedClass() throws IOException {
        String actual = render(ToolAccessScanner.scan(indexOfQlawkusExtensions()));

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Files.writeString(Path.of("src", "test", "resources", SNAPSHOT), actual);
        }
        assertEquals(pinned(), actual,
                "Tool classification changed. If that is intended, rerun with -D" + UPDATE_PROPERTY + "=true");
    }

    @Test
    void theSnapshotCoversTheToolsOfTheKnownExtensions() throws IOException {
        List<String> tools = pinned().lines().map(line -> line.split("\t")[0]).toList();

        List<String> expected = List.of("readFile", "runCommand", "sendEmail", "shareFile", "trashFile",
                "deleteEvent", "scheduleTask", "installSkillFromText", "fetchUrl", "queryMetrics", "createSpecialist",
                "registerArtifact", "configureHeartbeat", "addAchievement", "configureMcpConnection");
        assertTrue(tools.containsAll(expected), "missing from the snapshot: " + expected.stream()
                .filter(tool -> !tools.contains(tool)).toList());
    }

    private static String render(List<ToolAccessEntry> entries) {
        return entries.stream()
                .map(entry -> entry.tool() + "\t" + entry.accessClass() + "\t" + entry.source() + "\n")
                .collect(Collectors.joining());
    }

    private static String pinned() throws IOException {
        try (InputStream in = ToolAccessSnapshotTest.class.getClassLoader().getResourceAsStream(SNAPSHOT)) {
            assertFalse(in == null, SNAPSHOT + " is missing; run with -D" + UPDATE_PROPERTY + "=true to create it");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Index indexOfQlawkusExtensions() throws IOException {
        Indexer indexer = new Indexer();
        for (Path root : qlawkusExtensionRoots()) {
            if (Files.isDirectory(root)) {
                indexDirectory(indexer, root);
            } else {
                indexJar(indexer, root);
            }
        }
        return indexer.complete();
    }

    private static List<Path> qlawkusExtensionRoots() throws IOException {
        List<Path> roots = new ArrayList<>();
        for (URL descriptor : Collections.list(
                ToolAccessSnapshotTest.class.getClassLoader().getResources("META-INF/quarkus-extension.properties"))) {
            Properties properties = new Properties();
            try (InputStream in = descriptor.openStream()) {
                properties.load(in);
            }
            String artifact = properties.getProperty("deployment-artifact", "");
            if (artifact.startsWith(QLAWKUS_GROUP)) {
                roots.add(rootOf(descriptor));
            }
        }
        return roots;
    }

    private static Path rootOf(URL descriptor) {
        try {
            if ("jar".equals(descriptor.getProtocol())) {
                String spec = descriptor.getFile();
                return Paths.get(new URI(spec.substring(0, spec.indexOf("!/"))));
            }
            Path file = Paths.get(descriptor.toURI());
            return file.getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void indexDirectory(Indexer indexer, Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (file.toString().endsWith(".class")) {
                    try (InputStream in = Files.newInputStream(file)) {
                        indexer.index(in);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void indexJar(Indexer indexer, Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile());
                Stream<? extends ZipEntry> entries = zip.stream()) {
            for (var entry : entries.filter(e -> e.getName().endsWith(".class")).toList()) {
                try (InputStream in = zip.getInputStream(entry)) {
                    indexer.index(in);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
