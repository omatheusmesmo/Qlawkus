package dev.omatheusmesmo.qlawkus.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "qlawkus.agent")
public interface AgentConfig {

    /**
     * IANA timezone used to render the current date and time injected into the system prompt,
     * so the agent resolves relative expressions ("today", "tomorrow") against the owner's clock.
     */
    @WithDefault("America/Sao_Paulo")
    String timezone();

    /**
     * Idle time-to-live, in minutes, for the single shared conversation context across all channels.
     */
    @WithDefault("60")
    long contextTtlMinutes();

    /**
     * One shared conversation across every channel (the agent serves a single owner).
     */
    SharedContext sharedContext();

    /**
     * The {@code searchMemories} tool: explicit retrieval over long-term facts.
     */
    Memory memory();

    /**
     * Active memory: query-relevant facts injected before every reply, with no tool call needed.
     */
    ActiveMemory activeMemory();

    /**
     * Archival of each message as a searchable transcript embedding ({@code source=transcript}).
     */
    TranscriptArchive transcriptArchive();

    /**
     * The {@code searchTranscripts} tool: retrieval over archived raw transcripts.
     */
    TranscriptSearch transcriptSearch();

    /**
     * Passive fact extraction: after each completed turn, mine durable facts from the
     * conversation and store them ({@code source=semantic-extractor}), on every channel.
     */
    SemanticExtractor semanticExtractor();

    /**
     * Markdown fact backend: where the agent's {@code .md} fact files and their embedding cache
     * live. Used only when {@code qlawkus.cognition.backend=markdown}.
     */
    Facts facts();

    interface Facts {

        /**
         * Directory holding the markdown fact files and the sibling embedding cache
         * ({@code .embeddings.json}). Defaults to a qlawkus-owned folder, keeping facts editable
         * and git-versionable.
         */
        @WithDefault("${user.home}/.qlawkus/facts")
        String root();
    }

    interface SharedContext {

        /**
         * Whether all channels share one conversation context. When false, each channel keeps its own.
         */
        @WithDefault("true")
        boolean enabled();
    }

    interface Memory {

        /**
         * Maximum number of facts returned by the {@code searchMemories} tool.
         */
        @WithDefault("10")
        int maxResults();

        /**
         * Minimum cosine score for a fact to be returned by the {@code searchMemories} tool.
         */
        @WithDefault("0.7")
        double minScore();
    }

    interface ActiveMemory {

        /**
         * Whether to inject query-relevant facts into the prompt before each reply.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Maximum number of facts injected per turn by active memory.
         */
        @WithDefault("5")
        int maxResults();

        /**
         * Minimum cosine score for a fact to be injected by active memory.
         */
        @WithDefault("0.7")
        double minScore();
    }

    interface TranscriptArchive {

        /**
         * Whether each user and AI message is archived as a searchable transcript embedding.
         */
        @WithDefault("true")
        boolean enabled();
    }

    interface SemanticExtractor {

        /**
         * Whether to mine durable facts from each completed conversation turn.
         */
        @WithDefault("true")
        boolean enabled();
    }

    interface TranscriptSearch {

        /**
         * Maximum number of transcript excerpts returned by the {@code searchTranscripts} tool.
         */
        @WithDefault("5")
        int maxResults();

        /**
         * Minimum cosine score for a transcript excerpt to be returned by the {@code searchTranscripts} tool.
         */
        @WithDefault("0.7")
        double minScore();
    }
}
