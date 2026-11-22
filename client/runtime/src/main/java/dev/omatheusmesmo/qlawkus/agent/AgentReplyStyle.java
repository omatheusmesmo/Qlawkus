package dev.omatheusmesmo.qlawkus.agent;

import java.util.Locale;

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
        String languageContext = language == null ? "" : languageContext();
        if (format != Format.SPEECH) return languageContext;
        return "\n\n## Spoken conversation\n\n"
                + "This reply will be spoken aloud automatically. Answer in natural, conversational sentences "
                + "that are easy to understand by listening. Do not use Markdown formatting, headings, bullet or "
                + "numbered lists, tables, code blocks, raw URLs or emoji. Explain code or structured data in words "
                + "rather than reading its syntax. Do not call a separate voice-delivery tool for this turn."
                + languageContext;
    }

    private String languageContext() {
        String name = Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH);
        String label = name.equals(language) ? "the language with code " + language : name + " (" + language + ")";
        return "\n\n## Required language for this reply\n\n"
                + "Write the entire reply in " + label + ". This choice is specific to the current turn "
                + "and overrides language preferences or examples in the persona, profile, previous messages, "
                + "retrieved memory and tool results. Use this language for the greeting, explanation and closing.";
    }
}
