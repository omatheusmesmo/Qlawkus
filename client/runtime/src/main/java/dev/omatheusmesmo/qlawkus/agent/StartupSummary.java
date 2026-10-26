package dev.omatheusmesmo.qlawkus.agent;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Three INFO lines at boot that say what is running: the provider and the models of each chat tier, the
 * embedding model and its dimension, and the runtime switches (cognition backend, observability,
 * self-observation, messaging channels). Only names and switches are written. A token or a key never
 * reaches the lines, and a channel appears only when its credential is configured, not what it is.
 */
@ApplicationScoped
public class StartupSummary {

    private static final String UNSET = "unset";
    private static final String NONE = "none";

    @Inject
    Instance<MeterRegistry> registry;

    void onStart(@Observes StartupEvent event) {
        Config config = ConfigProvider.getConfig();
        Settings settings = new Settings(
                optional(config, "qlawkus.openai.kind").orElse(null),
                optional(config, "quarkus.langchain4j.openai.\"primary\".chat-model.model-name").orElse(null),
                optional(config, "quarkus.langchain4j.openai.\"secondary\".chat-model.model-name").orElse(null),
                optional(config, "quarkus.langchain4j.ollama.\"fallback\".chat-model.model-name").orElse(null),
                optional(config, "qlawkus.embedding.model-name").orElse(null),
                optional(config, "qlawkus.embedding.dimensions").orElse(null),
                optional(config, "qlawkus.cognition.backend").orElse("markdown"),
                optional(config, "qlawkus.self-observe.enabled").map(Boolean::valueOf).orElse(false),
                !registry.isUnsatisfied(),
                configuredChannels(config));
        lines(settings).forEach(line -> Log.info(line));
    }

    /** The lines written at boot, in order. Pure, so the content is testable without a runtime. */
    public static List<String> lines(Settings settings) {
        return List.of(
                "Agent models: provider=%s primary=%s secondary=%s fallback=%s".formatted(
                        or(settings.providerKind()), or(settings.primaryModel()),
                        or(settings.secondaryModel()), or(settings.fallbackModel())),
                "Agent embeddings: model=%s dimension=%s".formatted(
                        or(settings.embeddingModel()), or(settings.embeddingDimensions())),
                "Agent runtime: cognition=%s self-observe=%s observability=%s channels=%s".formatted(
                        or(settings.cognitionBackend()), onOff(settings.selfObserve()),
                        onOff(settings.observability()),
                        settings.channels().isEmpty() ? NONE : String.join(",", settings.channels())));
    }

    private static List<String> configuredChannels(Config config) {
        return Stream.of("discord", "telegram")
                .filter(channel -> optional(config, "qlawkus.messaging." + channel + ".bot-token").isPresent())
                .toList();
    }

    private static Optional<String> optional(Config config, String key) {
        return config.getOptionalValue(key, String.class).filter(value -> !value.isBlank());
    }

    private static String or(String value) {
        return value == null ? UNSET : value;
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    /**
     * The values the lines are built from. A {@code null} means the setting is absent, and it is shown
     * as unset rather than as the word null.
     */
    public record Settings(String providerKind, String primaryModel, String secondaryModel, String fallbackModel,
            String embeddingModel, String embeddingDimensions, String cognitionBackend, boolean selfObserve,
            boolean observability, List<String> channels) {
    }
}
