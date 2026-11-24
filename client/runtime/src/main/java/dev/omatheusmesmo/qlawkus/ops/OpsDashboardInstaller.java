package dev.omatheusmesmo.qlawkus.ops;

import io.quarkus.vertx.http.ManagementInterface;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Registers the management-port route when Quarkus exposes the management router.
 */
@ApplicationScoped
public class OpsDashboardInstaller {

    private final OpsDashboard dashboard;

    @Inject
    OpsDashboardInstaller(OpsDashboard dashboard) {
        this.dashboard = dashboard;
    }

    void onManagementReady(@Observes ManagementInterface management) {
        management.router().get("/dashboard").handler(dashboard::serve);
    }
}
