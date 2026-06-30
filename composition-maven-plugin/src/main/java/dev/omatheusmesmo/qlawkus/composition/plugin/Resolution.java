package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.util.List;

/**
 * The outcome of applying a manifest's build-time posture over the capability catalog: which
 * capabilities are composed in, which are left out, and any {@code except} entries that name no
 * known capability (surfaced so the build report and validation can flag a typo).
 *
 * @param selected capabilities to compose into the pom
 * @param excluded capabilities deliberately left out
 * @param unknownExcept {@code except} names that match no catalog capability
 */
public record Resolution(
        List<Capability> selected,
        List<Capability> excluded,
        List<String> unknownExcept) {

    public Resolution {
        selected = List.copyOf(selected);
        excluded = List.copyOf(excluded);
        unknownExcept = List.copyOf(unknownExcept);
    }

    /**
     * The coordinates the generator should ensure are present in the pom.
     */
    public List<Coordinates> selectedCoordinates() {
        return selected.stream().map(Capability::coordinates).toList();
    }
}
