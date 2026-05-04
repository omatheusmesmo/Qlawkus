package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.logging.Log;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;

import java.io.IOException;

public class GoogleAuthHeadersFilter implements ClientRequestFilter {

    @Inject
    Instance<CredentialVaultService> vaultInstance;

    @Override
    public void filter(ClientRequestContext context) throws IOException {
        if (vaultInstance.isUnsatisfied()) {
            Log.warn("CredentialVaultService not available, skipping Authorization header");
            return;
        }
        String token = vaultInstance.get().getAccessToken();
        if (token != null) {
            context.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        } else {
            Log.warn("No Google access token available, request will proceed without Authorization header");
        }
    }
}
