package dev.omatheusmesmo.qlawkus.agent;

/** Presentation guidance for one turn, independent of the channel that requested it. */
public record AgentReplyStyle(Format format, String language) {

    public enum Format { TEXT, SPEECH }

    public static final AgentReplyStyle DEFAULT = new AgentReplyStyle(Format.TEXT, null);

    public AgentReplyStyle {
        if (format == null || language != null && !language.matches("[a-z]{2,3}")) {
            throw new IllegalArgumentException("Invalid reply style or language code");
        }
    }

    public static AgentReplyStyle speech(String language) {
        return new AgentReplyStyle(Format.SPEECH, language);
    }

    public String systemContext() {
        String languageContext = language == null ? "" : "\n\nReply in language code " + language + ".";
        if (format != Format.SPEECH) return languageContext;
        return "\n\n## Spoken conversation\n\n"
                + "This reply will be spoken aloud automatically. Answer in natural, conversational sentences "
                + "that are easy to understand by listening. Do not use Markdown formatting, headings, bullet or "
                + "numbered lists, tables, code blocks, raw URLs or emoji. Explain code or structured data in words "
                + "rather than reading its syntax. Do not call a separate voice-delivery tool for this turn."
                + languageContext;
    }
}
