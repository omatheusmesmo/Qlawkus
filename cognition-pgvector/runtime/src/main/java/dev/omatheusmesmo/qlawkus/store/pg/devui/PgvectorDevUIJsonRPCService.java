package dev.omatheusmesmo.qlawkus.store.pg.devui;

import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import dev.omatheusmesmo.qlawkus.store.SoulStore;
import dev.omatheusmesmo.qlawkus.store.UserProfileStore;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionMigrator;
import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionReconciler;
import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionReconciler.Direction;
import dev.omatheusmesmo.qlawkus.store.pg.reconcile.CognitionReconciler.Stats;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backs the pgvector extension's Dev UI card. Its reason to exist is that this extension is what
 * makes the cognition stores database-backed, and the only way to see which backend actually won the
 * build-time selection is to ask the container which implementation each SPI resolved to.
 *
 * <p>Reconcile and migrate delegate to the same beans the {@code /api/admin/cognition} endpoints
 * call. Migrate overwrites destination singletons, so it is exposed as two explicit directions
 * rather than one ambiguous button.
 */
public class PgvectorDevUIJsonRPCService {

    @Inject
    CognitionReconciler reconciler;

    @Inject
    CognitionMigrator migrator;

    @Inject
    FactStore factStore;

    @Inject
    EpisodicStore episodicStore;

    @Inject
    WorkingMemoryStore workingMemoryStore;

    @Inject
    SoulStore soulStore;

    @Inject
    UserProfileStore userProfileStore;

    /**
     * Which implementation each cognition SPI resolved to this build. The names are the honest
     * answer to "am I on Postgres?", which no config value can give on its own: the markdown stores
     * are {@code @DefaultBean}, so the backend depends on both the config switch and whether this
     * extension is on the classpath at all.
     */
    public Map<String, Object> getBackends() {
        Map<String, Object> backends = new LinkedHashMap<>();
        backends.put("FactStore", implName(factStore));
        backends.put("EpisodicStore", implName(episodicStore));
        backends.put("WorkingMemoryStore", implName(workingMemoryStore));
        backends.put("SoulStore", implName(soulStore));
        backends.put("UserProfileStore", implName(userProfileStore));
        return backends;
    }

    /** The non-destructive union of files and pgvector, in both directions. */
    public Map<String, Object> reconcile() {
        return asMap(reconciler.reconcileAll());
    }

    /** One-directional copy that overwrites the destination singletons. */
    public Map<String, Object> migrateFilesToPg() {
        return asMap(migrator.migrate(Direction.FILES_TO_PG));
    }

    public Map<String, Object> migratePgToFiles() {
        return asMap(migrator.migrate(Direction.PG_TO_FILES));
    }

    private static Map<String, Object> asMap(Stats stats) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toPg", stats.toPg());
        result.put("toFiles", stats.toFiles());
        return result;
    }

    private static String implName(Object bean) {
        Class<?> type = io.quarkus.arc.ClientProxy.unwrap(bean).getClass();
        while (type.getName().contains("_Subclass")) {
            type = type.getSuperclass();
        }
        return type.getSimpleName();
    }
}
