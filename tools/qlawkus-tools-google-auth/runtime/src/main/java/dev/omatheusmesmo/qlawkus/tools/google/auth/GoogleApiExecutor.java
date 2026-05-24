package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class GoogleApiExecutor {

    @Inject
    Instance<CredentialVaultService> vaultInstance;

    public <T> T executeWithAuthRetry(GoogleApiCall<T> call) throws Exception {
        try {
            return call.execute();
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 401 && !vaultInstance.isUnsatisfied()) {
                Log.infof("Google API returned 401, forcing token refresh and retrying");
                vaultInstance.get().forceRenewAccessToken();
                return call.execute();
            }
            if (status == 403 && !vaultInstance.isUnsatisfied()) {
                Log.infof("Google API returned 403, attempting token refresh before giving up");
                vaultInstance.get().forceRenewAccessToken();
                try {
                    return call.execute();
                } catch (WebApplicationException retryEx) {
                    if (retryEx.getResponse().getStatus() == 403) {
                        Log.warnf("Google API still 403 after token refresh — likely insufficient scope");
                    }
                    throw retryEx;
                }
            }
            throw e;
        } catch (Exception e) {
            throw e;
        }
    }

    @FunctionalInterface
    public interface GoogleApiCall<T> {
        T execute() throws Exception;
    }
}
