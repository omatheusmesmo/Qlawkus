package dev.omatheusmesmo.qlawkus.console.onboarding;

import dev.omatheusmesmo.qlawkus.security.AdminSetupService;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Optional;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * While no admin password exists, nothing can authenticate, so a browser opening the console would meet a
 * credentials prompt it can never satisfy. This sends such a visit to {@code /console/first-run} instead.
 * Only page navigations are redirected: the setup page itself and anything with a file extension (the
 * console's stylesheet, scripts and fonts, which are public) pass through untouched. Once the password is
 * set the filter does nothing, and a deployment without the Argon2 admin has no setup service to ask.
 */
public class FirstRunRedirect {

    private static final String CONSOLE = "/console";
    private static final String FIRST_RUN = "/console/first-run";

    @Inject
    Instance<AdminSetupService> setup;

    @ServerRequestFilter(preMatching = true)
    public Optional<Response> redirect(ContainerRequestContext request) {
        String path = request.getUriInfo().getRequestUri().getPath();
        if (!isConsolePage(path) || !"GET".equals(request.getMethod()) || !setupOpen()) {
            return Optional.empty();
        }
        return Optional.of(Response.seeOther(URI.create(FIRST_RUN)).build());
    }

    private boolean setupOpen() {
        return setup.isResolvable() && setup.get().required();
    }

    static boolean isConsolePage(String path) {
        if (!path.equals(CONSOLE) && !path.startsWith(CONSOLE + "/")) {
            return false;
        }
        if (path.equals(FIRST_RUN) || path.startsWith(FIRST_RUN + "/")) {
            return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return last.indexOf('.') < 0;
    }
}
