package dev.omatheusmesmo.qlawkus.composition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositionManifestParserTest {

    private static final String FULL = """
            version: 1
            build-time:
              default: enabled
              except:
                - brag
                - skill-hub
            runtime:
              skill-hub.approval-mode: hitl
              semantic-extractor.enabled: true
            """;

    @Test
    void parsesEveryField() {
        CompositionManifest m = CompositionManifestParser.parse(FULL);

        assertEquals(1, m.version());
        assertEquals(Posture.ENABLED, m.buildTime().defaultPosture());
        assertEquals(java.util.List.of("brag", "skill-hub"), m.buildTime().except());
        assertEquals("hitl", m.runtime().get("skill-hub.approval-mode"));
        assertEquals(Boolean.TRUE, m.runtime().get("semantic-extractor.enabled"));
    }

    @Test
    void policyEnabledDefault_exceptListIsDisabled() {
        BuildTime bt = CompositionManifestParser.parse(FULL).buildTime();

        assertFalse(bt.isEnabled("brag"));
        assertFalse(bt.isEnabled("skill-hub"));
        assertTrue(bt.isEnabled("messaging.discord"));
    }

    @Test
    void policyDisabledDefault_exceptListIsEnabled() {
        BuildTime bt = CompositionManifestParser.parse("""
                version: 1
                build-time:
                  default: disabled
                  except:
                    - messaging.discord
                """).buildTime();

        assertTrue(bt.isEnabled("messaging.discord"));
        assertFalse(bt.isEnabled("brag"));
        assertFalse(bt.isEnabled("skill-hub"));
    }

    @Test
    void roundTripsThroughRender() {
        CompositionManifest original = CompositionManifestParser.parse(FULL);

        CompositionManifest reparsed = CompositionManifestParser.parse(
                CompositionManifestParser.render(original));

        assertEquals(original, reparsed);
    }

    @Test
    void emptyExceptDefaultsToEmptyList() {
        BuildTime bt = CompositionManifestParser.parse("""
                version: 1
                build-time:
                  default: enabled
                """).buildTime();

        assertTrue(bt.except().isEmpty());
        assertTrue(bt.isEnabled("brag"));
    }

    @Test
    void rejectsUnsupportedVersion() {
        InvalidManifestException e = assertThrows(InvalidManifestException.class,
                () -> CompositionManifestParser.parse("""
                        version: 2
                        build-time:
                          default: enabled
                        """));
        assertTrue(e.getMessage().contains("version"));
    }

    @Test
    void rejectsMissingBuildTime() {
        assertThrows(InvalidManifestException.class,
                () -> CompositionManifestParser.parse("version: 1\n"));
    }

    @Test
    void rejectsUnknownPosture() {
        InvalidManifestException e = assertThrows(InvalidManifestException.class,
                () -> CompositionManifestParser.parse("""
                        version: 1
                        build-time:
                          default: sometimes
                        """));
        assertTrue(e.getMessage().contains("enabled"));
    }

    @Test
    void rejectsEmptyDocument() {
        assertThrows(InvalidManifestException.class,
                () -> CompositionManifestParser.parse(""));
    }
}
