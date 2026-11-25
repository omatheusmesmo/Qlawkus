package dev.omatheusmesmo.qlawkus.tools.brag;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

/**
 * Whether the Brag database is switched on for this boot, and the way into it.
 *
 * <p>The extension rides in the published image without a database; its datasource stays off until
 * {@code QUARKUS_DATASOURCE_BRAG_JDBC_URL} is given. Opening a transaction on the persistence unit of an
 * inactive datasource throws, and an {@code @Transactional} method opens it before its body runs, so the
 * callers ask here first and open the transaction themselves with {@link #inTransaction}.
 */
@ApplicationScoped
public class BragDatabase {

    /** What the agent is told when it calls a Brag tool on a boot without the database. */
    public static final String UNAVAILABLE = "The Brag document needs its own database, which is not configured on "
            + "this boot. Set QUARKUS_DATASOURCE_BRAG_JDBC_URL and restart to use it.";

    /** Whether the database is configured for this boot. */
    public boolean isActive() {
        return Arc.container().instance(AgroalDataSource.class, new DataSource.DataSourceLiteral("brag"))
                .getBean().isActive();
    }

    /** Runs {@code work} in the caller's transaction, or in a new one when there is none. */
    public <T> T inTransaction(Supplier<T> work) {
        return QuarkusTransaction.joiningExisting().call(work::get);
    }
}
