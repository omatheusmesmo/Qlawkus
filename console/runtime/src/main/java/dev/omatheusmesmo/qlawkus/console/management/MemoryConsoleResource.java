package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.dto.JournalSummary;
import dev.omatheusmesmo.qlawkus.store.EpisodicStore;
import dev.omatheusmesmo.qlawkus.store.FactStore;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * The memory management page. Reads directly from the client-side stores ({@link FactStore},
 * {@link EpisodicStore}) to render a rich view; the mutating actions (review, curate, purge) are
 * driven from the page by HTMX against the existing {@code /api/admin/memory} endpoints, so this
 * resource only renders and never re-implements the admin logic.
 */
@Path("/console/memory")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class MemoryConsoleResource {

    private static final int RECENT_FACTS = 20;

    private final Template memory;
    private final FactStore factStore;
    private final EpisodicStore episodicStore;

    public MemoryConsoleResource(@Location("console/memory.html") Template memory,
                                 FactStore factStore,
                                 EpisodicStore episodicStore) {
        this.memory = memory;
        this.factStore = factStore;
        this.episodicStore = episodicStore;
    }

    @GET
    public TemplateInstance page() {
        List<String> facts = factStore.listFactTexts(RECENT_FACTS);
        List<JournalSummary> journals = episodicStore.listJournals();
        return memory
                .data("active", "memory")
                .data("facts", facts)
                .data("sources", factStore.listSources())
                .data("journals", journals)
                .data("journalCount", episodicStore.count());
    }
}
