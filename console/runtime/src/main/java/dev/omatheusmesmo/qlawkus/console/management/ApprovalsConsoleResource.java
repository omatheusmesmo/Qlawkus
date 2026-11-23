package dev.omatheusmesmo.qlawkus.console.management;

import dev.omatheusmesmo.qlawkus.approval.ApprovalStore;
import dev.omatheusmesmo.qlawkus.dto.ApprovalView;
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
 * The owner's approval queue. A read view over the store: the decisions themselves are driven from the
 * page by HTMX against {@code /api/admin/approvals}, so the console never re-implements a decision.
 */
@Path("/console/approvals")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class ApprovalsConsoleResource {

    private final Template approvals;
    private final ApprovalStore store;

    public ApprovalsConsoleResource(@Location("console/approvals.html") Template approvals, ApprovalStore store) {
        this.approvals = approvals;
        this.store = store;
    }

    @GET
    public TemplateInstance page() {
        List<ApprovalView> requests = store.list().stream().map(ApprovalView::summary).toList();
        long pending = requests.stream().filter(request -> "PENDING".equals(request.status())).count();
        return approvals
                .data("active", "approvals")
                .data("requests", requests)
                .data("pendingCount", pending);
    }
}
