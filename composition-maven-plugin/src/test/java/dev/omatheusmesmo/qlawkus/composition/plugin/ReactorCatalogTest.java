package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactorCatalogTest {

    @TempDir
    Path tmp;

    @Test
    void readsCapabilityFromDescriptor() throws IOException {
        ReactorModule brag = module("dev.omatheusmesmo", "qlawkus-tools-brag", """
                name: "Brag Document"
                metadata:
                  qlawkus:
                    capability: "brag"
                  keywords:
                    - "career"
                """);

        List<Capability> all = new ReactorCatalog(List.of(brag)).all();

        assertEquals(
                List.of(new Capability("brag", new Coordinates("dev.omatheusmesmo", "qlawkus-tools-brag"))),
                all);
    }

    @Test
    void skipsModuleWithoutCapabilityKey() throws IOException {
        ReactorModule core = module("dev.omatheusmesmo", "qlawkus-client", """
                metadata:
                  capabilities:
                    provides:
                      - "dev.omatheusmesmo.qlawkus.agent"
                """);

        assertTrue(new ReactorCatalog(List.of(core)).all().isEmpty());
    }

    @Test
    void skipsModuleWithoutDescriptorFile() {
        ReactorModule missing = new ReactorModule(
                "dev.omatheusmesmo", "qlawkus-messaging-core", tmp.resolve("does-not-exist.yaml"));

        assertTrue(new ReactorCatalog(List.of(missing)).all().isEmpty());
    }

    @Test
    void sameCapabilityFromSeveralModulesYieldsOneEntryEach() throws IOException {
        ReactorModule gmail = module("dev.omatheusmesmo", "qlawkus-tools-google-gmail", googleWorkspace());
        ReactorModule calendar = module("dev.omatheusmesmo", "qlawkus-tools-google-calendar", googleWorkspace());
        ReactorModule drive = module("dev.omatheusmesmo", "qlawkus-tools-google-drive", googleWorkspace());

        List<Capability> all = new ReactorCatalog(List.of(gmail, calendar, drive)).all();

        assertEquals(3, all.size());
        assertTrue(all.stream().allMatch(c -> c.name().equals("google-workspace")));
        assertEquals(
                List.of("qlawkus-tools-google-gmail", "qlawkus-tools-google-calendar", "qlawkus-tools-google-drive"),
                all.stream().map(c -> c.coordinates().artifactId()).toList());
    }

    private static String googleWorkspace() {
        return """
                metadata:
                  qlawkus:
                    capability: "google-workspace"
                """;
    }

    private ReactorModule module(String groupId, String artifactId, String descriptor) throws IOException {
        Path dir = Files.createDirectories(tmp.resolve(artifactId).resolve("META-INF"));
        Path file = dir.resolve("quarkus-extension.yaml");
        Files.writeString(file, descriptor);
        return new ReactorModule(groupId, artifactId, file);
    }
}
