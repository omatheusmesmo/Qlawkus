package dev.omatheusmesmo.qlawkus.tools.mcp;

/**
 * What the registry already knows about one connection, without contacting anyone: its id and a
 * {@link Condition}. Nothing else about a connection (url, headers, aliases) is part of this view.
 */
public record McpConnectionHealth(String id, Condition condition) {

    /**
     * Where a connection stands. Only {@link #UNREACHABLE} and {@link #NEEDS_SIGN_IN} degrade the agent:
     * a connection nobody has used yet is connected lazily on the first turn, and a disabled one is the
     * owner's decision.
     */
    public enum Condition {
        CONNECTED(null),
        NOT_CONTACTED_YET(null),
        DISABLED(null),
        UNREACHABLE("unreachable"),
        NEEDS_SIGN_IN("needs sign-in");

        private final String phrase;

        Condition(String phrase) {
            this.phrase = phrase;
        }

        public boolean degrades() {
            return phrase != null;
        }

        public String phrase() {
            return phrase;
        }
    }
}
