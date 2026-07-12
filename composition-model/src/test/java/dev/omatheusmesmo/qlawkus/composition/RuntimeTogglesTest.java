package dev.omatheusmesmo.qlawkus.composition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeTogglesTest {

    @Test
    void flattensVerbatimDottedKeysUnchanged() {
        Map<String, Object> toggles = new LinkedHashMap<>();
        toggles.put("qlawkus.skill-hub.approval-mode", "hitl");
        toggles.put("qlawkus.agent.semantic-extractor.enabled", false);

        Map<String, String> flat = RuntimeToggles.flatten(toggles);

        assertEquals("hitl", flat.get("qlawkus.skill-hub.approval-mode"));
        assertEquals("false", flat.get("qlawkus.agent.semantic-extractor.enabled"));
    }

    @Test
    void flattensNestedMapsToDottedKeys() {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("approval-mode", "yolo");
        Map<String, Object> mid = new LinkedHashMap<>();
        mid.put("skill-hub", child);
        Map<String, Object> toggles = new LinkedHashMap<>();
        toggles.put("qlawkus", mid);

        Map<String, String> flat = RuntimeToggles.flatten(toggles);

        assertEquals("yolo", flat.get("qlawkus.skill-hub.approval-mode"));
    }

    @Test
    void listsBecomeIndexedKeys() {
        Map<String, Object> toggles = new LinkedHashMap<>();
        toggles.put("qlawkus.agent.roots", List.of("a", "b"));

        Map<String, String> flat = RuntimeToggles.flatten(toggles);

        assertEquals("a", flat.get("qlawkus.agent.roots[0]"));
        assertEquals("b", flat.get("qlawkus.agent.roots[1]"));
    }

    @Test
    void nullValuesAreDropped() {
        Map<String, Object> toggles = new LinkedHashMap<>();
        toggles.put("qlawkus.present", "x");
        toggles.put("qlawkus.absent", null);

        Map<String, String> flat = RuntimeToggles.flatten(toggles);

        assertEquals(Map.of("qlawkus.present", "x"), flat);
    }

    @Test
    void nullTreeYieldsEmpty() {
        assertTrue(RuntimeToggles.flatten(null).isEmpty());
    }

    @Test
    void parseOverrideReadsAndFlattensYaml() {
        Map<String, String> flat = RuntimeToggles.parseOverride("""
                qlawkus.skill-hub.approval-mode: hitl
                qlawkus.agent.semantic-extractor.enabled: false
                """);

        assertEquals("hitl", flat.get("qlawkus.skill-hub.approval-mode"));
        assertEquals("false", flat.get("qlawkus.agent.semantic-extractor.enabled"));
    }

    @Test
    void parseOverrideIsLenientOnBlank() {
        assertTrue(RuntimeToggles.parseOverride("").isEmpty());
        assertTrue(RuntimeToggles.parseOverride("   ").isEmpty());
        assertTrue(RuntimeToggles.parseOverride(null).isEmpty());
    }

    @Test
    void parseOverrideRejectsMalformedYaml() {
        assertThrows(InvalidManifestException.class,
                () -> RuntimeToggles.parseOverride("key: : : broken\n  - nope"));
    }

    @Test
    void renderOverrideThenParseOverrideRoundTrips() {
        Map<String, String> toggles = new LinkedHashMap<>();
        toggles.put("qlawkus.skill-hub.approval-mode", "hitl");
        toggles.put("qlawkus.agent.semantic-extractor.enabled", "false");
        toggles.put("qlawkus.messaging.tts.providers.pt.voice", "value: with a colon");

        String rendered = RuntimeToggles.renderOverride(toggles);
        Map<String, String> reparsed = RuntimeToggles.parseOverride(rendered);

        assertEquals(toggles, reparsed);
    }

    @Test
    void renderOverrideOfEmptyMapYieldsEmptyDocument() {
        assertTrue(RuntimeToggles.parseOverride(RuntimeToggles.renderOverride(Map.of())).isEmpty());
        assertTrue(RuntimeToggles.parseOverride(RuntimeToggles.renderOverride(null)).isEmpty());
    }
}
