package dev.omatheusmesmo.qlawkus.it.console;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.console.ObservabilityLink;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A template reads a record through reflection in a JVM and through a generated resolver in a native
 * image, where the reflective path finds no property and the page answers 500. So every record the
 * console ships must have the resolver Qute generates for it, which is what the console processor
 * asks for. Qute names the resolver of a nested record {@code Outer$_Inner_ValueResolver}.
 */
@QuarkusTest
class ConsoleTemplateTypesTest {

    private static final String CONSOLE_PACKAGE = "dev/omatheusmesmo/qlawkus/console/";

    @Test
    void everyConsoleRecordHasAGeneratedResolver() throws Exception {
        List<String> missing = new ArrayList<>();
        List<String> records = consoleRecordNames();

        assertTrue(records.size() > 10, "the scan found " + records.size() + " records, expected the console views");
        for (String name : records) {
            if (!Class.forName(name).isRecord()) {
                continue;
            }
            String resolver = name.replace("$", "$_") + "_ValueResolver";
            try {
                Class.forName(resolver);
            } catch (ClassNotFoundException absent) {
                missing.add(name);
            }
        }

        assertTrue(missing.isEmpty(), "records without a generated Qute resolver: " + missing);
    }

    private static List<String> consoleRecordNames() throws IOException, URISyntaxException {
        Path root = Path.of(ObservabilityLink.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> files = Files.walk(root)) {
                files.map(root::relativize).map(Path::toString)
                        .filter(ConsoleTemplateTypesTest::isConsoleClass)
                        .forEach(entry -> names.add(toClassName(entry)));
            }
        } else {
            try (JarFile jar = new JarFile(root.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String entry = entries.nextElement().getName();
                    if (isConsoleClass(entry)) {
                        names.add(toClassName(entry));
                    }
                }
            }
        }
        return names;
    }

    private static boolean isConsoleClass(String entry) {
        return entry.startsWith(CONSOLE_PACKAGE) && entry.endsWith(".class") && !entry.endsWith("package-info.class");
    }

    private static String toClassName(String entry) {
        return entry.substring(0, entry.length() - ".class".length()).replace('/', '.').replace('\\', '.');
    }
}
