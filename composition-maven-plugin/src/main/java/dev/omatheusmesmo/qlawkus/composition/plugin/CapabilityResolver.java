package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.util.ArrayList;
import java.util.List;

import dev.omatheusmesmo.qlawkus.composition.BuildTime;

/**
 * Applies a manifest's build-time posture over a {@link CapabilityCatalog} to decide which
 * capabilities are composed in. The posture logic itself lives in {@link BuildTime#isEnabled} (the
 * shared composition-model), so the generator and the running app cannot disagree on what a manifest
 * means - this class only walks the catalog and partitions it.
 */
public final class CapabilityResolver {

    private final CapabilityCatalog catalog;

    public CapabilityResolver(CapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Partitions the catalog into selected and excluded capabilities for the given posture, and
     * collects any {@code except} entry that names no known capability.
     */
    public Resolution resolve(BuildTime buildTime) {
        List<Capability> selected = new ArrayList<>();
        List<Capability> excluded = new ArrayList<>();
        List<String> known = new ArrayList<>();

        for (Capability capability : catalog.all()) {
            known.add(capability.name());
            if (buildTime.isEnabled(capability.name())) {
                selected.add(capability);
            } else {
                excluded.add(capability);
            }
        }

        List<String> unknownExcept = buildTime.except().stream()
                .filter(name -> !known.contains(name))
                .toList();

        return new Resolution(selected, excluded, unknownExcept);
    }
}
