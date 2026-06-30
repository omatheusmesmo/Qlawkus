package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;

/**
 * Reconciles a pom's {@code <dependencies>} block to a {@link Resolution}, in place. Only the
 * capabilities the catalog owns are touched: selected ones are added if missing, deselected ones are
 * removed if present, and every other dependency (quarkus-arc, the BOM-managed runtime, etc.) is left
 * untouched - that is the "skeleton" the generator preserves.
 *
 * <p>Added dependencies carry no {@code <version>}: capability extensions are expected to be managed
 * by the platform/BOM in the pom skeleton, exactly as {@code quarkus extension add} does.
 */
public final class PomComposer {

    private PomComposer() {
    }

    /**
     * Brings {@code model}'s dependency block in line with {@code resolution}.
     *
     * @return true if the model was changed (so a no-op run never rewrites the file)
     */
    public static boolean compose(Model model, Resolution resolution) {
        Set<String> selected = new LinkedHashSet<>();
        resolution.selectedCoordinates().forEach(c -> selected.add(key(c)));

        Set<String> managed = new LinkedHashSet<>(selected);
        resolution.excluded().forEach(c -> managed.add(key(c.coordinates())));

        boolean changed = removeDeselected(model, managed, selected);
        changed |= addMissing(model, resolution.selectedCoordinates());
        return changed;
    }

    private static boolean removeDeselected(Model model, Set<String> managed, Set<String> selected) {
        boolean changed = false;
        for (Iterator<Dependency> it = model.getDependencies().iterator(); it.hasNext(); ) {
            Dependency dependency = it.next();
            String dependencyKey = key(dependency.getGroupId(), dependency.getArtifactId());
            if (managed.contains(dependencyKey) && !selected.contains(dependencyKey)) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean addMissing(Model model, List<Coordinates> selectedCoordinates) {
        Set<String> present = new LinkedHashSet<>();
        model.getDependencies().forEach(d -> present.add(key(d.getGroupId(), d.getArtifactId())));

        boolean changed = false;
        for (Coordinates coordinates : selectedCoordinates) {
            if (present.add(key(coordinates))) {
                model.addDependency(toDependency(coordinates));
                changed = true;
            }
        }
        return changed;
    }

    private static Dependency toDependency(Coordinates coordinates) {
        Dependency dependency = new Dependency();
        dependency.setGroupId(coordinates.groupId());
        dependency.setArtifactId(coordinates.artifactId());
        return dependency;
    }

    private static String key(Coordinates coordinates) {
        return key(coordinates.groupId(), coordinates.artifactId());
    }

    private static String key(String groupId, String artifactId) {
        return groupId + ":" + artifactId;
    }
}
