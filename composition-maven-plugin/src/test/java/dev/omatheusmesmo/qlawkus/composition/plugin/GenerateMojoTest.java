package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateMojoTest {

    private static final CapabilityCatalog CATALOG = () -> List.of(
            new Capability("brag", new Coordinates("dev.omatheusmesmo", "qlawkus-tools-brag")),
            new Capability("messaging.discord", new Coordinates("dev.omatheusmesmo", "qlawkus-messaging-discord")));

    private static final String SKELETON_POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>dev.omatheusmesmo</groupId>
              <artifactId>my-agent</artifactId>
              <version>1.0.0</version>
              <dependencies>
                <dependency>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-arc</artifactId>
                </dependency>
              </dependencies>
            </project>
            """;

    @TempDir
    Path dir;

    private GenerateMojo mojoFor(Path pom, Path manifest) {
        GenerateMojo mojo = new GenerateMojo();
        mojo.setCatalog(CATALOG);
        mojo.setPom(pom.toFile());
        mojo.manifest = manifest.toFile();
        mojo.basedir = dir.toFile();
        return mojo;
    }

    private Model readPom(Path pom) throws Exception {
        try (Reader reader = Files.newBufferedReader(pom)) {
            return new MavenXpp3Reader().read(reader);
        }
    }

    private boolean has(Model model, String artifactId) {
        return model.getDependencies().stream().anyMatch(d -> d.getArtifactId().equals(artifactId));
    }

    @Test
    void generatesDependencyBlockFromManifest() throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), SKELETON_POM);
        Path manifest = Files.writeString(dir.resolve("agent.yml"), """
                version: 1
                build-time:
                  default: disabled
                  except:
                    - messaging.discord
                """);

        mojoFor(pom, manifest).execute();

        Model model = readPom(pom);
        assertTrue(has(model, "qlawkus-messaging-discord"), "selected capability added");
        assertFalse(has(model, "qlawkus-tools-brag"), "deselected capability absent");
        assertTrue(has(model, "quarkus-arc"), "skeleton dependency preserved");
    }

    @Test
    void rerunIsIdempotent() throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), SKELETON_POM);
        Path manifest = Files.writeString(dir.resolve("agent.yml"), """
                version: 1
                build-time:
                  default: enabled
                  except:
                    - brag
                """);

        mojoFor(pom, manifest).execute();
        String afterFirst = Files.readString(pom);
        mojoFor(pom, manifest).execute();
        String afterSecond = Files.readString(pom);

        assertEquals(afterFirst, afterSecond, "second run leaves the pom byte-identical");
    }
}
