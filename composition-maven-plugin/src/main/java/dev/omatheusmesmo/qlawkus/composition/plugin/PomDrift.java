package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.util.List;

/**
 * Dependencies the generator would add or remove to reconcile the pom with a {@link Resolution}. Used
 * for drift detection before rewriting the pom on disk.
 */
public record PomDrift(List<String> added, List<String> removed) {

    public PomDrift {
        added = List.copyOf(added);
        removed = List.copyOf(removed);
    }

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
    }
}
