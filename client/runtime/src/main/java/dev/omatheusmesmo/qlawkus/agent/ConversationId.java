package dev.omatheusmesmo.qlawkus.agent;

/**
 * Stable chat-memory identifiers. A single shared conversation gives the owner
 * continuity across every interface (Discord, Telegram, REST), so a message in
 * one channel can be continued in another.
 */
public final class ConversationId {

    /** The owner's shared conversation, used by all user-facing entry points when sharing is on. */
    public static final String SHARED = "qlawkus-shared";

    /** Internal agent reflections (e.g. startup), kept out of the user thread. */
    public static final String SYSTEM = "qlawkus-system";

    /** Isolated conversation for the REST API when shared context is disabled. */
    public static final String REST = "qlawkus-rest";

    private ConversationId() {
    }
}
