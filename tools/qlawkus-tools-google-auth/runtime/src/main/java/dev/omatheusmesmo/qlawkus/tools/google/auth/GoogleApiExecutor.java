package dev.omatheusmesmo.qlawkus.tools.google.auth;

import dev.omatheusmesmo.qlawkus.cognition.GoogleAuthRequiredIndicator;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

@ApplicationScoped
public class GoogleApiExecutor {

    @Inject
    Instance<CredentialVaultService> vaultInstance;

    @Inject
    Instance<GoogleAuthRequiredIndicator> authRequiredIndicator;

    public <T> T executeWithAuthRetry(GoogleApiCall<T> call) throws Exception {
        try {
            return call.execute();
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 401 && !vaultInstance.isUnsatisfied()) {
                Log.infof("Google API returned 401, forcing token refresh and retrying");
                vaultInstance.get().forceRenewAccessToken();
                try {
                    return call.execute();
                } catch (WebApplicationException retryEx) {
                    if (retryEx.getResponse().getStatus() == 401 || retryEx.getResponse().getStatus() == 403) {
                        Log.warnf("Google API still auth error after token refresh, arming HITL gate");
                        markAuthNeeded();
                    }
                    throw retryEx;
                }
            }
            if (status == 403 && !vaultInstance.isUnsatisfied()) {
                Log.warnf("Google API returned 403 — likely insufficient scope, arming HITL gate");
                markAuthNeeded();
                throw e;
            }
            throw e;
        }
    }

    private void markAuthNeeded() {
        if (!authRequiredIndicator.isUnsatisfied()) {
            authRequiredIndicator.get().markAuthRequired();
        }
    }

    @FunctionalInterface
    public interface GoogleApiCall<T> {
        T execute() throws Exception;
    }
}
