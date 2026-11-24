package dev.omatheusmesmo.qlawkus.deployment;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jackson 3 reads record components and enum creators reflectively, which a native image only allows
 * for registered types. composition-model has no Quarkus dependency, so {@link ClientProcessor} registers
 * its types; this fails when a record or enum is added there without being registered.
 */
class CompositionModelReflectionTest {

    private static final String PACKAGE_PATH = "dev/omatheusmesmo/qlawkus/composition";

    @Test
    void everyRecordAndEnumOfTheCompositionModelIsRegisteredWithItsMembers() throws Exception {
        ReflectiveClassBuildItem registered = new ClientProcessor().registerCompositionModelForReflection();

        Set<String> expected = modelTypes();

        assertFalse(expected.isEmpty(), "no record or enum found in composition-model, so the scan is broken");
        assertEquals(expected, new TreeSet<>(registered.getClassNames()),
                "composition-model types Jackson binds must all be registered for native reflection");
        assertTrue(registered.isConstructors(), "records are built through their canonical constructor");
        assertTrue(registered.isMethods(), "record component accessors and enum creators are called reflectively");
    }

    private static Set<String> modelTypes() throws Exception {
        URL anchor = CompositionManifest.class.getClassLoader()
                .getResource(PACKAGE_PATH + "/CompositionManifest.class");
        URI uri = anchor.toURI();
        Set<String> names = new TreeSet<>();
        if ("jar".equals(uri.getScheme())) {
            try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                collect(fs.getPath("/" + PACKAGE_PATH), names);
            }
        } else {
            collect(Path.of(uri).getParent(), names);
        }
        return names;
    }

    private static void collect(Path directory, Set<String> names) throws IOException, ClassNotFoundException {
        List<Path> classFiles;
        try (Stream<Path> files = Files.list(directory)) {
            classFiles = files.filter(file -> file.getFileName().toString().endsWith(".class")).toList();
        }
        for (Path file : classFiles) {
            String simpleName = file.getFileName().toString().replace(".class", "");
            Class<?> type = Class.forName("dev.omatheusmesmo.qlawkus.composition." + simpleName, false,
                    CompositionManifest.class.getClassLoader());
            if (type.isRecord() || type.isEnum()) {
                names.add(type.getName());
            }
        }
    }
}
