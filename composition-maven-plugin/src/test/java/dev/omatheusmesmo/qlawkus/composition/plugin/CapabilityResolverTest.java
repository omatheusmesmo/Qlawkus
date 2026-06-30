package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.util.List;

import dev.omatheusmesmo.qlawkus.composition.BuildTime;
import dev.omatheusmesmo.qlawkus.composition.Posture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityResolverTest {

    private static final Capability BRAG =
            new Capability("brag", new Coordinates("dev.omatheusmesmo", "qlawkus-tools-brag"));
    private static final Capability SKILL_HUB =
            new Capability("skill-hub", new Coordinates("dev.omatheusmesmo", "qlawkus-tools-skill-hub"));
    private static final Capability DISCORD =
            new Capability("messaging.discord", new Coordinates("dev.omatheusmesmo", "qlawkus-messaging-discord"));

    private final CapabilityResolver resolver =
            new CapabilityResolver(() -> List.of(BRAG, SKILL_HUB, DISCORD));

    @Test
    void enabledDefault_exceptIsExcluded() {
        Resolution r = resolver.resolve(new BuildTime(Posture.ENABLED, List.of("brag", "skill-hub")));

        assertEquals(List.of(DISCORD), r.selected());
        assertEquals(List.of(BRAG, SKILL_HUB), r.excluded());
        assertEquals(
                List.of(new Coordinates("dev.omatheusmesmo", "qlawkus-messaging-discord")),
                r.selectedCoordinates());
    }

    @Test
    void disabledDefault_exceptIsSelected() {
        Resolution r = resolver.resolve(new BuildTime(Posture.DISABLED, List.of("messaging.discord")));

        assertEquals(List.of(DISCORD), r.selected());
        assertEquals(List.of(BRAG, SKILL_HUB), r.excluded());
    }

    @Test
    void enabledDefault_noExcept_selectsEverything() {
        Resolution r = resolver.resolve(new BuildTime(Posture.ENABLED, List.of()));

        assertEquals(List.of(BRAG, SKILL_HUB, DISCORD), r.selected());
        assertTrue(r.excluded().isEmpty());
    }

    @Test
    void unknownExceptNameIsSurfaced() {
        Resolution r = resolver.resolve(new BuildTime(Posture.ENABLED, List.of("brag", "typo-name")));

        assertEquals(List.of("typo-name"), r.unknownExcept());
    }
}
