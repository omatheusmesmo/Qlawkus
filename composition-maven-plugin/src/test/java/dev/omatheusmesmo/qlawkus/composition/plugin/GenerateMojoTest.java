package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        mojo.failOnUnknownCapability = true;
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

    private MavenProject reactorModule(String artifactId, String capability) throws Exception {
        Path moduleDir = dir.resolve(artifactId);
        Path descriptor = Files.createDirectories(moduleDir.resolve("src/main/resources/META-INF"))
                .resolve("quarkus-extension.yaml");
        Files.writeString(descriptor, "metadata:\n  qlawkus:\n    capability: \"" + capability + "\"\n");
        MavenProject project = new MavenProject();
        project.setGroupId("dev.omatheusmesmo");
        project.setArtifactId(artifactId);
        project.setFile(moduleDir.resolve("pom.xml").toFile());
        return project;
    }

    @Test
    void buildsCatalogFromReactorModulesWhenNoCatalogInjected() throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), SKELETON_POM);
        Path manifest = Files.writeString(dir.resolve("agent.yml"), """
                version: 1
                build-time:
                  default: disabled
                  except:
                    - messaging.discord
                """);

        GenerateMojo mojo = new GenerateMojo();
        mojo.setPom(pom.toFile());
        mojo.manifest = manifest.toFile();
        mojo.basedir = dir.toFile();
        mojo.reactorProjects = List.of(
                reactorModule("qlawkus-tools-brag", "brag"),
                reactorModule("qlawkus-messaging-discord", "messaging.discord"));

        mojo.execute();

        Model model = readPom(pom);
        assertTrue(has(model, "qlawkus-messaging-discord"), "capability read from reactor descriptor is added");
        assertFalse(has(model, "qlawkus-tools-brag"), "deselected reactor capability absent");
        assertTrue(has(model, "quarkus-arc"), "skeleton dependency preserved");
    }

    @Test
    void unknownExceptFailsTheBuild() throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), SKELETON_POM);
        Path manifest = Files.writeString(dir.resolve("agent.yml"), """
                version: 1
                build-time:
                  default: disabled
                  except:
                    - messaging.discrod
                """);

        MojoExecutionException error =
                assertThrows(MojoExecutionException.class, () -> mojoFor(pom, manifest).execute());
        assertTrue(error.getMessage().contains("messaging.discrod"), "names the offending entry");
        assertEquals(SKELETON_POM, Files.readString(pom), "pom is left untouched when validation fails");
    }

    @Test
    void unknownExceptWithOptOutWarnsAndProceeds() throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), SKELETON_POM);
        Path manifest = Files.writeString(dir.resolve("agent.yml"), """
                version: 1
                build-time:
                  default: disabled
                  except:
                    - messaging.discord
                    - messaging.discrod
                """);

        GenerateMojo mojo = mojoFor(pom, manifest);
        mojo.failOnUnknownCapability = false;
        mojo.execute();

        Model model = readPom(pom);
        assertTrue(has(model, "qlawkus-messaging-discord"), "known capability still reconciled");
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
