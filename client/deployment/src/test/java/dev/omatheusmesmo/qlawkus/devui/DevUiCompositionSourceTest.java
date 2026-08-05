package dev.omatheusmesmo.qlawkus.devui;

import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.InvalidManifestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the capability toggle. The manifest is policy-plus-exceptions, so the same request means the
 * opposite edit under each posture: selecting a capability adds it to {@code except} when the default
 * is disabled and removes it when the default is enabled. Getting that backwards would silently
 * compose the wrong agent, which no compiler catches.
 */
class DevUiCompositionSourceTest {

    @TempDir
    Path tmp;

    @Test
    void underDisabledDefault_selectingAddsToExcept() throws IOException {
        DevUiCompositionSource source = sourceWith("""
                version: 1
                build-time:
                  default: disabled
                  except:
                    - brag
                """);

        CompositionManifest updated = source.setCapability("console", true);

        assertTrue(updated.buildTime().except().contains("console"));
        assertTrue(updated.buildTime().isEnabled("console"));
        assertTrue(updated.buildTime().isEnabled("brag"), "the untouched capability keeps its state");
    }

    @Test
    void underDisabledDefault_deselectingRemovesFromExcept() throws IOException {
        DevUiCompositionSource source = sourceWith("""
                version: 1
                build-time:
                  default: disabled
                  except:
                    - brag
                    - console
                """);

        CompositionManifest updated = source.setCapability("brag", false);

        assertFalse(updated.buildTime().except().contains("brag"));
        assertFalse(updated.buildTime().isEnabled("brag"));
        assertTrue(updated.buildTime().isEnabled("console"));
    }

    @Test
    void underEnabledDefault_deselectingAddsToExcept() throws IOException {
        DevUiCompositionSource source = sourceWith("""
                version: 1
                build-time:
                  default: enabled
                  except:
                    - brag
                """);

        CompositionManifest updated = source.setCapability("console", false);

        assertTrue(updated.buildTime().except().contains("console"),
                "under an enabled default, except lists what is turned off");
        assertFalse(updated.buildTime().isEnabled("console"));
    }

    @Test
    void underEnabledDefault_selectingRemovesFromExcept() throws IOException {
        DevUiCompositionSource source = sourceWith("""
                version: 1
                build-time:
                  default: enabled
                  except:
                    - brag
                """);

        CompositionManifest updated = source.setCapability("brag", true);

        assertFalse(updated.buildTime().except().contains("brag"));
        assertTrue(updated.buildTime().isEnabled("brag"));
    }

    @Test
    void togglingIsIdempotent() throws IOException {
        DevUiCompositionSource source = sourceWith("""
                version: 1
                build-time:
                  default: disabled
                  except:
                    - brag
                """);

        source.setCapability("brag", true);
        CompositionManifest updated = source.setCapability("brag", true);

        assertEquals(1, updated.buildTime().except().stream().filter("brag"::equals).count(),
                "selecting an already selected capability must not duplicate the entry");
    }

    @Test
    void theEditIsPersistedAndReparses() throws IOException {
        Path manifest = tmp.resolve("agent.yml");
        DevUiCompositionSource source = sourceWith("""
                version: 1
                build-time:
                  default: disabled
                """, manifest);

        source.setCapability("console", true);

        CompositionManifest reread = CompositionManifestParser.parse(manifest);
        assertTrue(reread.buildTime().isEnabled("console"),
                "the toggle must survive a round trip through the file, since dev mode reads it back");
    }

    @Test
    void runtimeTogglesSurviveACapabilityEdit() throws IOException {
        DevUiCompositionSource source = sourceWith("""
                version: 1
                build-time:
                  default: disabled
                runtime:
                  qlawkus.rt.example: kept
                """);

        CompositionManifest updated = source.setCapability("console", true);

        assertEquals("kept", updated.runtime().get("qlawkus.rt.example"),
                "editing the build-time tier must not drop the runtime tier");
    }

    @Test
    void replaceRejectsAnInvalidManifestBeforeTouchingDisk() throws IOException {
        Path manifest = tmp.resolve("agent.yml");
        String original = """
                version: 1
                build-time:
                  default: disabled
                """;
        DevUiCompositionSource source = sourceWith(original, manifest);

        assertThrows(InvalidManifestException.class,
                () -> source.replace("version: 99\nbuild-time:\n  default: disabled\n"));
        assertEquals(original, Files.readString(manifest), "a rejected manifest must not be written");
    }

    @Test
    void theHeaderCommentSurvivesAnEdit() throws IOException {
        Path manifest = tmp.resolve("agent.yml");
        DevUiCompositionSource source = sourceWith("""
                # Composition manifest for this distribution.
                # Explains why the policy is what it is.
                version: 1
                build-time:
                  default: disabled
                """, manifest);

        source.setCapability("console", true);

        String written = Files.readString(manifest);
        assertTrue(written.startsWith("# Composition manifest for this distribution."),
                "a hand-authored header must not be lost to a one-line toggle");
        assertTrue(written.contains("# Explains why the policy is what it is."));
        assertTrue(CompositionManifestParser.parse(manifest).buildTime().isEnabled("console"));
    }

    @Test
    void anAbsentManifestIsNotWritable() {
        DevUiCompositionSource source = new DevUiCompositionSource(tmp.resolve("missing.yml"));

        assertFalse(source.isWritable());
        assertThrows(IllegalStateException.class, () -> source.setCapability("console", true));
    }

    @Test
    void aNullPathIsNotWritable() {
        DevUiCompositionSource source = new DevUiCompositionSource(null);

        assertFalse(source.isWritable());
        assertEquals("", source.location());
    }

    private DevUiCompositionSource sourceWith(String yaml) throws IOException {
        return sourceWith(yaml, tmp.resolve("agent.yml"));
    }

    private DevUiCompositionSource sourceWith(String yaml, Path manifest) throws IOException {
        Files.writeString(manifest, yaml);
        return new DevUiCompositionSource(manifest);
    }
}
