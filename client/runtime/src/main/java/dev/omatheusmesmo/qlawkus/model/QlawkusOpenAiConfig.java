package dev.omatheusmesmo.qlawkus.model;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

import java.util.Optional;

/**
 * Provider profile selector for the OpenAI-compatible primary model, in the spirit of {@code db-kind}.
 *
 * <p>This is intentionally thin: it only adds what quarkus-langchain4j does NOT already provide. Setting
 * {@code qlawkus.openai.kind} resolves the base URL plus opinionated default chat/embedding models for
 * that provider. To override any individual value, use the native quarkus-langchain4j keys
 * ({@code quarkus.langchain4j.openai."primary".*}) or the friendly bootstrap aliases
 * ({@code LLM_API_KEY}, {@code LLM_BASE_URL}, {@code LLM_CHAT_MODEL}, {@code LLM_EMBEDDING_MODEL},
 * {@code EMBEDDING_DIMENSION}) — Qlawkus does not duplicate those as new properties. Resolution happens
 * at config bootstrap in {@code LlmKindConfigSourceFactory}, which emits the native keys at a low ordinal
 * so explicit overrides always win.
 */
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.openai")
public interface QlawkusOpenAiConfig {

    /**
     * OpenAI-compatible provider profile: {@code openai} (the default when unset), {@code nvidia},
     * {@code deepseek}, {@code mistral}, {@code groq}, {@code xai} or {@code openrouter}. Selects the
     * base URL and the opinionated default chat/embedding models. Override individual values via the
     * native {@code quarkus.langchain4j.openai."primary".*} keys or {@code LLM_*} env aliases. The
     * Ollama fallback is configured separately under {@code quarkus.langchain4j.ollama."fallback".*}.
     */
    Optional<String> kind();

    /**
     * Embedding provider profile. Defaults to {@link #kind()}, but can target a different
     * OpenAI-compatible provider — e.g. chat on a chat-only provider (groq, xai, openrouter) with
     * embeddings on openai. Env alias: {@code LLM_EMBEDDING_KIND}.
     */
    Embedding embedding();

    interface Embedding {

        /**
         * OpenAI-compatible provider profile for embeddings. When unset, follows {@code qlawkus.openai.kind}.
         */
        Optional<String> kind();
    }
}
