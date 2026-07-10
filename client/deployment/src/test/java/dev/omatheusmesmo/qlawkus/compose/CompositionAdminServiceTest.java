package dev.omatheusmesmo.qlawkus.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.omatheusmesmo.qlawkus.composition.InvalidManifestException;
import dev.omatheusmesmo.qlawkus.dto.CompositionState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompositionAdminServiceTest {

    private static final String VALID = """
            version: 1
            build-time:
              default: disabled
              except:
                - messaging.discord
            """;

    private CompositionAdminService serviceIn(Path dir) {
        return new CompositionAdminService(dir.resolve("agent.staged.yml"));
    }

    @Test
    void stagesAValidManifestAndExposesIt(@TempDir Path dir) throws IOException {
        CompositionAdminService service = serviceIn(dir);

        service.stage(VALID);

        assertTrue(Files.exists(dir.resolve("agent.staged.yml")), "staged file must be written");
        CompositionState state = service.currentState();
        assertEquals(VALID, state.staged(), "GET must reflect the staged manifest verbatim");
        assertNotNull(state.stagedAt(), "a staged manifest carries a timestamp");
    }

    @Test
    void rejectsAnInvalidVersionWithoutTouchingDisk(@TempDir Path dir) {
        CompositionAdminService service = serviceIn(dir);

        assertThrows(InvalidManifestException.class,
                () -> service.stage("version: 2\nbuild-time:\n  default: disabled\n"));
        assertFalse(Files.exists(dir.resolve("agent.staged.yml")),
                "a rejected manifest must never be staged");
    }

    @Test
    void rejectsMalformedYaml(@TempDir Path dir) {
        CompositionAdminService service = serviceIn(dir);

        assertThrows(InvalidManifestException.class, () -> service.stage("::: not yaml :::"));
    }

    @Test
    void rejectsAManifestMissingTheBuildTimeSection(@TempDir Path dir) {
        CompositionAdminService service = serviceIn(dir);

        assertThrows(InvalidManifestException.class, () -> service.stage("version: 1\n"));
    }

    @Test
    void discardsAStagedManifest(@TempDir Path dir) throws IOException {
        CompositionAdminService service = serviceIn(dir);
        service.stage(VALID);

        assertTrue(service.discardStaged(), "discarding a present manifest returns true");
        assertFalse(service.discardStaged(), "discarding when none is staged returns false");
        assertNull(service.currentState().staged(), "state shows no staged manifest after discard");
    }
}
