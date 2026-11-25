package dev.omatheusmesmo.qlawkus.console.onboarding;

import dev.omatheusmesmo.qlawkus.security.AdminSetupService;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.resteasy.reactive.RestForm;

/**
 * The browser form of the first-run admin setup, at {@code /console/first-run}. It is open without
 * credentials because, before the password exists, there are none; {@link FirstRunRedirect} sends an
 * anonymous visit here. Everything that matters is enforced by {@link AdminSetupService}: setup is open
 * only while no password is set and only with the one-time token printed in the agent log. This resource
 * only collects the fields, checks the confirmation and shows the outcome.
 */
@Path("/console/first-run")
@PermitAll
@Produces(MediaType.TEXT_HTML)
public class FirstRunResource {

    private static final String USERNAME_PROPERTY = "qlawkus.admin.username";

    private final Template page;
    private final Instance<AdminSetupService> setup;

    public FirstRunResource(@Location("console/first-run.html") Template page,
                            Instance<AdminSetupService> setup) {
        this.page = page;
        this.setup = setup;
    }

    @GET
    public Response show() {
        if (!setupOpen()) {
            return toConsole();
        }
        return Response.ok(view(null, false)).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response submit(@RestForm String token, @RestForm String password, @RestForm String confirm) {
        if (!setupOpen()) {
            return toConsole();
        }
        if (password == null || !password.equals(confirm)) {
            return Response.ok(view("The two passwords do not match.", false)).build();
        }
        try {
            setup.get().complete(token, password.toCharArray());
        } catch (AdminSetupService.SetupClosedException closed) {
            return toConsole();
        } catch (AdminSetupService.InvalidTokenException | AdminSetupService.NotPersistedException
                 | IllegalArgumentException refused) {
            return Response.ok(view(refused.getMessage(), false)).build();
        }
        return Response.ok(view(null, true)).build();
    }

    private TemplateInstance view(String error, boolean done) {
        return page
                .data("error", error)
                .data("done", done)
                .data("username", ConfigProvider.getConfig()
                        .getOptionalValue(USERNAME_PROPERTY, String.class).orElse("qlawkus"))
                .data("minLength", AdminSetupService.MIN_PASSWORD_LENGTH);
    }

    private boolean setupOpen() {
        return setup.isResolvable() && setup.get().required();
    }

    private static Response toConsole() {
        return Response.seeOther(URI.create("/console")).build();
    }
}
