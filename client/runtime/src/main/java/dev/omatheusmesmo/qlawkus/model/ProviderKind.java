package dev.omatheusmesmo.qlawkus.model;

import java.util.Optional;

/**
 * Built-in profiles for OpenAI-compatible LLM providers, selected via {@code qlawkus.llm.kind}
 * (and optionally {@code qlawkus.llm.embedding.kind}), in the spirit of {@code quarkus.datasource.db-kind}.
 *
 * <p>Each kind is pure data: a base URL plus opinionated, current defaults for the chat and embedding
 * models. Every field is overridable through the {@code LLM_*} environment variables, so these defaults
 * only fill the blanks. All kinds map onto the single {@code quarkus-langchain4j-openai} extension;
 * Ollama is intentionally absent (it has its own provider namespace and serves as the fallback layer).
 *
 * <p>Chat model names move fast and are meant to be refreshed here periodically; they are always
 * overridable via {@code LLM_CHAT_MODEL}. Embedding model/dimension/input-type are stable per provider.
 */
public enum ProviderKind {

    // sendsDimensionsParam: only OpenAI's text-embedding-3-* accept the `dimensions` request param
    // (Matryoshka reduction). Native-dimension models (NVIDIA nv-embedqa, mistral-embed, ollama mxbai)
    // REJECT it, so it must not be sent for them.
    OPENAI("https://api.openai.com/v1/", "gpt-5-mini", "text-embedding-3-large", 1024, null, true),
    NVIDIA("https://integrate.api.nvidia.com/v1", "nvidia/nemotron-3-ultra-550b-a55b", "nvidia/nv-embedqa-e5-v5", 1024, "passage", false),
    DEEPSEEK("https://api.deepseek.com", "deepseek-v4-flash", "deepseek-embed", 1024, null, false),
    MISTRAL("https://api.mistral.ai/v1", "mistral-large-latest", "mistral-embed", 1024, null, false),
    GROQ("https://api.groq.com/openai/v1", "qwen/qwen3.6-27b", null, 0, null, false),
    XAI("https://api.x.ai/v1", "grok-4.3", null, 0, null, false),
    OPENROUTER("https://openrouter.ai/api/v1", "openrouter/owl-alpha", null, 0, null, false);

    private final String baseUrl;
    private final String chatModel;
    private final String embeddingModel;
    private final int embeddingDimensions;
    private final String embeddingInputType;
    private final boolean sendsDimensionsParam;

    ProviderKind(String baseUrl, String chatModel, String embeddingModel, int embeddingDimensions,
            String embeddingInputType, boolean sendsDimensionsParam) {
        this.baseUrl = baseUrl;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        this.embeddingInputType = embeddingInputType;
        this.sendsDimensionsParam = sendsDimensionsParam;
    }

    /**
     * Whether this provider accepts the OpenAI {@code dimensions} request parameter. Only true for
     * OpenAI text-embedding-3-* (Matryoshka). Native-dimension providers reject it.
     */
    public boolean sendsDimensionsParam() {
        return sendsDimensionsParam;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Optional<String> chatModel() {
        return Optional.ofNullable(chatModel);
    }

    public Optional<String> embeddingModel() {
        return Optional.ofNullable(embeddingModel);
    }

    public int embeddingDimensions() {
        return embeddingDimensions;
    }

    /**
     * Provider-specific embedding request parameter (NVIDIA's {@code input_type}), expressed via
     * langchain4j {@code customParameters} since the quarkus-langchain4j openai config cannot.
     */
    public Optional<String> embeddingInputType() {
        return Optional.ofNullable(embeddingInputType);
    }

    public boolean hasEmbeddings() {
        return embeddingModel != null;
    }

    /**
     * Resolves a kind by name, case-insensitively.
     *
     * @throws IllegalArgumentException if no kind matches, listing the valid ones
     */
    public static ProviderKind of(String name) {
        for (ProviderKind kind : values()) {
            if (kind.name().equalsIgnoreCase(name)) {
                return kind;
            }
        }
        throw new IllegalArgumentException(
                "Unknown qlawkus.llm.kind '%s'. Valid kinds: %s".formatted(name, java.util.Arrays.toString(values())));
    }
}
