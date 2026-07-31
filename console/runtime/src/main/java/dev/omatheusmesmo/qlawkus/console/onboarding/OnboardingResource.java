package dev.omatheusmesmo.qlawkus.console.onboarding;

import dev.omatheusmesmo.qlawkus.compose.CompositionAdminService;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateData;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;

import java.util.ArrayList;
import java.util.List;

/**
 * The first-run onboarding wizard, served inside the console at {@code /console/setup}. It never
 * writes config or secrets itself: it drives the existing admin surfaces - {@link KeystoreSecretWriter}
 * (secrets, applied on restart) and {@link CompositionAdminService} (staged manifest, applied on
 * rebuild) - so the wizard is only an orchestrator of UI over primitives that already exist.
 *
 * <p>Two phases split by the closed-world boundary. Phase A (pre-rebuild): the LLM key/settings and
 * the capability selection. Phase B (post-rebuild): the interactive steps that only make sense once
 * a capability has been compiled in - messaging tokens and Google authorization - shown per
 * capability found in the baked manifest.
 */
@Path("/console/setup")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class OnboardingResource {

    private static final String LLM_API_KEY = "quarkus.langchain4j.openai.\"primary\".api-key";
    private static final String LLM_BASE_URL = "quarkus.langchain4j.openai.\"primary\".base-url";
    private static final String LLM_CHAT_MODEL = "quarkus.langchain4j.openai.\"primary\".chat-model.model-name";
    private static final String CONSOLE_CAPABILITY = "console";
    private static final String GOOGLE_CLIENT_ID = "qlawkus.google.auth.client-id";
    private static final String GOOGLE_CLIENT_SECRET = "qlawkus.google.auth.client-secret";

    private final Template setup;
    private final Template result;
    private final SetupState state;
    private final KeystoreSecretWriter secrets;
    private final CompositionAdminService composition;

    public OnboardingResource(@Location("console/setup.html") Template setup,
                              @Location("console/setup-result.html") Template result,
                              SetupState state,
                              KeystoreSecretWriter secrets,
                              CompositionAdminService composition) {
        this.setup = setup;
        this.result = result;
        this.state = state;
        this.secrets = secrets;
        this.composition = composition;
    }

    @GET
    public TemplateInstance page() {
        List<CapView> caps = new ArrayList<>();
        List<PhaseBView> phaseB = new ArrayList<>();
        for (ConsoleCapability c : ConsoleCapability.CATALOG) {
            boolean baked = state.capabilityBaked(c.name());
            caps.add(new CapView(c.name(), c.label(), c.description(), baked));
            if (baked && c.followUp() != ConsoleCapability.FollowUp.NONE) {
                phaseB.add(new PhaseBView(c.name(), c.label(), c.followUp().name(), c.tokenProperty(),
                        followUpReady(c)));
            }
        }
        return setup
                .data("llmConfigured", state.llmConfigured())
                .data("capabilities", caps)
                .data("phaseB", phaseB)
                .data("staged", isStaged());
    }

    @POST
    @Path("/llm")
    public TemplateInstance saveLlm(@RestForm String baseUrl, @RestForm String chatModel, @RestForm String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return err("An API key is required.");
        }
        try {
            secrets.setSecret(LLM_API_KEY, apiKey.trim());
            if (baseUrl != null && !baseUrl.isBlank()) {
                secrets.setSecret(LLM_BASE_URL, baseUrl.trim());
            }
            if (chatModel != null && !chatModel.isBlank()) {
                secrets.setSecret(LLM_CHAT_MODEL, chatModel.trim());
            }
            return ok("LLM settings saved to the keystore.", "Restart the app to apply them.");
        } catch (RuntimeException e) {
            return err("Could not save LLM settings: " + e.getMessage());
        }
    }

    @POST
    @Path("/capabilities")
    public TemplateInstance saveCapabilities(@RestForm List<String> capability) {
        List<String> selected = new ArrayList<>();
        if (capability != null) {
            selected.addAll(capability);
        }
        if (!selected.contains(CONSOLE_CAPABILITY)) {
            selected.add(CONSOLE_CAPABILITY);
        }
        try {
            composition.stage(manifestYaml(selected));
            return ok("Capability manifest staged.",
                    "Rebuild and redeploy to apply it (see examples/redeploy).");
        } catch (Exception e) {
            return err("Could not stage the manifest: " + e.getMessage());
        }
    }

    @POST
    @Path("/messaging")
    public TemplateInstance saveMessagingToken(@RestForm String property, @RestForm String token) {
        if (property == null || property.isBlank() || token == null || token.isBlank()) {
            return err("A token is required.");
        }
        try {
            secrets.setSecret(property.trim(), token.trim());
            return ok("Token saved to the keystore.", "Restart the app to connect the adapter.");
        } catch (RuntimeException e) {
            return err("Could not save the token: " + e.getMessage());
        }
    }

    /**
     * Whether a Phase B step has what it needs. A messaging adapter needs its token, which the wizard
     * itself can capture. Google needs an OAuth client, which identifies the deployment rather than
     * the owner and so arrives with the deployment (env, keystore, Vault) - the wizard reports whether
     * it is there and lets the owner authorize once it is, but never asks for it here.
     */
    private boolean followUpReady(ConsoleCapability capability) {
        return switch (capability.followUp()) {
            case MESSAGING_TOKEN -> state.secretPresent(capability.tokenProperty());
            case GOOGLE_OAUTH -> state.propertiesConfigured(GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET);
            case NONE -> false;
        };
    }

    private boolean isStaged() {
        try {
            return composition.currentState().staged() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String manifestYaml(List<String> except) {
        StringBuilder sb = new StringBuilder("version: 1\nbuild-time:\n  default: disabled\n  except:\n");
        for (String capability : except) {
            sb.append("    - ").append(capability).append('\n');
        }
        return sb.toString();
    }

    private TemplateInstance ok(String message, String detail) {
        return result.data("ok", true).data("message", message).data("detail", detail);
    }

    private TemplateInstance err(String message) {
        return result.data("ok", false).data("message", message).data("detail", "");
    }

    /** A capability row in the selection form: identity plus whether it is already compiled in. */
    @TemplateData
    public record CapView(String name, String label, String description, boolean enabled) {
    }

    /** A Phase B follow-up unlocked by a baked capability (a messaging token form or the Google card). */
    @TemplateData
    public record PhaseBView(String name, String label, String kind, String tokenProperty, boolean done) {
    }
}
