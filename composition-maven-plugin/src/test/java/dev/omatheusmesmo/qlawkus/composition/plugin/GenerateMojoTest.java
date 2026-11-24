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
    void emptyCatalogLeavesThePomUntouchedInsteadOfFailing() throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), SKELETON_POM);
        Path manifest = Files.writeString(dir.resolve("agent.yml"), """
                version: 1
                build-time:
                  default: disabled
                  except:
                    - messaging.discord
                    - brag
                """);

        GenerateMojo mojo = new GenerateMojo();
        mojo.setCatalog(List::of);
        mojo.setPom(pom.toFile());
        mojo.manifest = manifest.toFile();
        mojo.basedir = dir.toFile();
        mojo.failOnUnknownCapability = true;

        mojo.execute();

        assertEquals(SKELETON_POM, Files.readString(pom),
                "a standalone build with an empty catalog is a no-op, not a fail-on-unknown-capability error");
    }

    @Test
    void pomDriftFailsWithoutRewriting() throws Exception {
        Path pom = Files.writeString(dir.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>dev.omatheusmesmo</groupId>
                  <artifactId>my-agent</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>dev.omatheusmesmo</groupId>
                      <artifactId>qlawkus-tools-brag</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """);
        Path manifest = Files.writeString(dir.resolve("agent.yml"), """
                version: 1
                build-time:
                  default: disabled
                  except:
                    - messaging.discord
                """);

        GenerateMojo mojo = mojoFor(pom, manifest);
        mojo.setFailOnPomDrift(true);

        MojoExecutionException error =
                assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(error.getMessage().contains("would add"));
        assertTrue(error.getMessage().contains("qlawkus-messaging-discord"));
        assertTrue(error.getMessage().contains("would remove"));
        assertTrue(error.getMessage().contains("qlawkus-tools-brag"));
        String unchanged = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>dev.omatheusmesmo</groupId>
                  <artifactId>my-agent</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>dev.omatheusmesmo</groupId>
                      <artifactId>qlawkus-tools-brag</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """;
        assertEquals(unchanged, Files.readString(pom), "pom bytes unchanged on drift failure");
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

    @Test
    void updatesOnlyTheDirectDependencyEntries() throws Exception {
        String excluded = """
                    <dependency>
                      <groupId>dev.omatheusmesmo</groupId>
                      <artifactId>qlawkus-tools-brag</artifactId>
                    </dependency>
                """;
        String selected = excluded.replace("qlawkus-tools-brag", "qlawkus-messaging-discord");
        String directDependencies = """
                  <dependencies>
                    <!-- keep this: café -->
                    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-arc</artifactId></dependency>
                %s  </dependencies>
                """;
        String template = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>dev.omatheusmesmo</groupId>
                  <artifactId>my-agent</artifactId>
                  <version>1.0.0</version> <!-- keep the skeleton -->
                  <dependencyManagement>
                    <dependencies>
                %s    </dependencies>
                  </dependencyManagement>
                %s  <profiles>
                    <profile>
                      <id>example</id>
                      <dependencies>
                %s      </dependencies>
                    </profile>
                  </profiles>
                </project>
                """;
        String original = template.formatted(excluded.indent(4), directDependencies.formatted(excluded), excluded.indent(6));
        String expected = template.formatted(excluded.indent(4), directDependencies.formatted(selected), excluded.indent(6));
        Path pom = Files.writeString(dir.resolve("pom.xml"), original.replace("\n", "\r\n"));

        mojoFor(pom, discordManifest()).execute();

        assertEquals(expected.replace("\n", "\r\n"), Files.readString(pom));
    }

    @Test
    void createsADependencyBlockAndPreservesTheRestOfThePom() throws Exception {
        String original = SKELETON_POM.replaceFirst("(?s)  <dependencies>.*?</dependencies>\n", "")
                .replace("</project>", "  <!-- keep this -->\n</project>");
        Path pom = Files.writeString(dir.resolve("pom.xml"), original);

        mojoFor(pom, discordManifest()).execute();

        String updated = Files.readString(pom);
        assertTrue(updated.startsWith(original.substring(0, original.indexOf("</project>"))));
        assertTrue(has(readPom(pom), "qlawkus-messaging-discord"));
    }

    @Test
    void expandsASelfClosingDependenciesBlock() throws Exception {
        String original = SKELETON_POM.replaceFirst("(?s)<dependencies>.*?</dependencies>", "<dependencies />");
        Path pom = Files.writeString(dir.resolve("pom.xml"), original);

        mojoFor(pom, discordManifest()).execute();

        assertTrue(has(readPom(pom), "qlawkus-messaging-discord"));
        assertTrue(Files.readString(pom).startsWith(original.substring(0, original.indexOf("<dependencies"))));
    }

    private Path discordManifest() throws Exception {
        return Files.writeString(dir.resolve("agent.yml"), """
                version: 1
                build-time:
                  default: disabled
                  except:
                    - messaging.discord
                """);
    }
}
