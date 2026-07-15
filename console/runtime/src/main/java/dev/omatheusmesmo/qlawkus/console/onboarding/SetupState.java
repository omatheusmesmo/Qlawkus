package dev.omatheusmesmo.qlawkus.console.onboarding;

import dev.omatheusmesmo.qlawkus.composition.CompositionManifest;
import dev.omatheusmesmo.qlawkus.composition.CompositionManifestParser;
import dev.omatheusmesmo.qlawkus.composition.CompositionPaths;
import dev.omatheusmesmo.qlawkus.secrets.KeystoreSecretWriter;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * Answers "is this agent configured, and which onboarding steps remain?" by inference from the live
 * state: the LLM key in config, the capabilities in the baked manifest, and the aliases already in
 * the keystore. So the wizard needs no separate progress file, and it never couples to the tool
 * modules (it reads the baked manifest, it does not inject their beans). Read-only.
 */
@ApplicationScoped
public class SetupState {

    static final String LLM_API_KEY = "quarkus.langchain4j.openai.\"primary\".api-key";
    static final String LLM_BASE_URL = "quarkus.langchain4j.openai.\"primary\".base-url";
    private static final Set<String> PLACEHOLDERS = Set.of("", "dummy", "test-key", "changeme", "your-key");

    private final KeystoreSecretWriter secrets;

    public SetupState(KeystoreSecretWriter secrets) {
        this.secrets = secrets;
    }

    /**
     * Whether a usable LLM API key is configured: present and not one of the known placeholders that
     * a fresh install or a test fixture leaves in place.
     */
    public boolean llmConfigured() {
        String key = ConfigProvider.getConfig()
                .getOptionalValue(LLM_API_KEY, String.class)
                .orElse("")
                .trim();
        if (!PLACEHOLDERS.contains(key.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return localEndpoint();
    }

    /**
     * Whether the primary model points at a self-hosted, local endpoint (for example Ollama), where a
     * placeholder API key is expected and the agent can still reach a model. Without this, a working
     * local install (the {@code ./run.sh local} Ollama stack ships {@code LLM_API_KEY=dummy}) would be
     * nagged by the first-run banner to configure a key it does not need.
     */
    private boolean localEndpoint() {
        return isLocalEndpoint(ConfigProvider.getConfig()
                .getOptionalValue(LLM_BASE_URL, String.class)
                .orElse(""));
    }

    /** Whether {@code baseUrl} points at a loopback or self-hosted (Ollama) endpoint. */
    static boolean isLocalEndpoint(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        String normalized = baseUrl.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("localhost")
                || normalized.contains("127.0.0.1")
                || normalized.contains("://ollama")
                || normalized.contains(":11434");
    }

    /** The first-run signal that drives the console banner: the agent cannot talk to an LLM yet. */
    public boolean needsSetup() {
        return !llmConfigured();
    }

    /** Whether the given capability is enabled in the manifest baked into this running app. */
    public boolean capabilityBaked(String capability) {
        CompositionManifest manifest = bakedManifest();
        return manifest != null && manifest.buildTime().isEnabled(capability);
    }

    /** Whether a secret is already stored under the given alias (keystore, best-effort). */
    public boolean secretPresent(String alias) {
        try {
            return secrets.aliases().contains(alias);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static CompositionManifest bakedManifest() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(CompositionPaths.DEFAULT_MANIFEST)) {
            return in == null ? null : CompositionManifestParser.parse(in);
        } catch (Exception e) {
            return null;
        }
    }
}
