package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

/**
 * Whether the Google database is switched on for this boot, and the way into it.
 *
 * <p>The extension rides in the published image without a database; its datasource stays off until
 * {@code QUARKUS_DATASOURCE_GOOGLE_AUTH_JDBC_URL} is given. Opening a transaction on the persistence unit
 * of an inactive datasource throws, and an {@code @Transactional} method opens it before its body runs, so
 * the callers ask here first and open the transaction themselves with {@link #inTransaction}.
 */
@ApplicationScoped
public class GoogleAuthDatabase {

    /** Whether the database is configured for this boot. */
    public boolean isActive() {
        return Arc.container().instance(AgroalDataSource.class, new DataSource.DataSourceLiteral("google-auth"))
                .getBean().isActive();
    }

    /** Runs {@code work} in the caller's transaction, or in a new one when there is none. */
    public <T> T inTransaction(Supplier<T> work) {
        return QuarkusTransaction.joiningExisting().call(work::get);
    }
}
