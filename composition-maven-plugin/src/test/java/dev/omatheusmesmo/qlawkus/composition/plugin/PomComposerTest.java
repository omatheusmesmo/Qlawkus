package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PomComposerTest {

    private static final Capability BRAG =
            new Capability("brag", new Coordinates("dev.omatheusmesmo", "qlawkus-tools-brag"));
    private static final Capability SKILL_HUB =
            new Capability("skill-hub", new Coordinates("dev.omatheusmesmo", "qlawkus-tools-skill-hub"));
    private static final Capability DISCORD =
            new Capability("messaging.discord", new Coordinates("dev.omatheusmesmo", "qlawkus-messaging-discord"));

    private static Resolution resolution(List<Capability> selected, List<Capability> excluded) {
        return new Resolution(selected, excluded, List.of());
    }

    private static Model modelWith(String... ga) {
        Model model = new Model();
        for (String coords : ga) {
            String[] parts = coords.split(":");
            Dependency dependency = new Dependency();
            dependency.setGroupId(parts[0]);
            dependency.setArtifactId(parts[1]);
            model.addDependency(dependency);
        }
        return model;
    }

    private static boolean hasDependency(Model model, String groupId, String artifactId) {
        return model.getDependencies().stream()
                .anyMatch(d -> d.getGroupId().equals(groupId) && d.getArtifactId().equals(artifactId));
    }

    @Test
    void addsSelectedAndRemovesDeselected_leavingSkeletonUntouched() {
        Model model = modelWith("io.quarkus:quarkus-arc", "dev.omatheusmesmo:qlawkus-tools-brag");

        boolean changed = PomComposer.compose(model,
                resolution(List.of(DISCORD), List.of(BRAG, SKILL_HUB)));

        assertTrue(changed);
        assertTrue(hasDependency(model, "dev.omatheusmesmo", "qlawkus-messaging-discord"), "discord added");
        assertFalse(hasDependency(model, "dev.omatheusmesmo", "qlawkus-tools-brag"), "brag removed");
        assertTrue(hasDependency(model, "io.quarkus", "quarkus-arc"), "non-capability dep untouched");
    }

    @Test
    void addedDependencyHasNoVersion_bomManaged() {
        Model model = new Model();

        PomComposer.compose(model, resolution(List.of(DISCORD), List.of()));

        Dependency added = model.getDependencies().get(0);
        assertEquals("qlawkus-messaging-discord", added.getArtifactId());
        assertEquals(null, added.getVersion());
    }

    @Test
    void isIdempotent() {
        Model model = modelWith("io.quarkus:quarkus-arc");
        Resolution resolution = resolution(List.of(DISCORD), List.of(BRAG));

        assertTrue(PomComposer.compose(model, resolution), "first run changes the model");
        assertFalse(PomComposer.compose(model, resolution), "second run is a no-op");
    }

    @Test
    void leavesUnknownDepsAlone_onlyManagedCapabilitiesRemoved() {
        Model model = modelWith("org.example:not-a-capability");

        boolean changed = PomComposer.compose(model, resolution(List.of(), List.of(BRAG, SKILL_HUB)));

        assertFalse(changed);
        assertTrue(hasDependency(model, "org.example", "not-a-capability"));
    }
}
