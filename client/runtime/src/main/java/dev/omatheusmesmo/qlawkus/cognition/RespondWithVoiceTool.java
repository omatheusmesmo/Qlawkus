package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RespondWithVoiceTool {

    @Inject
    VoiceResponsePreference preference;

    @Tool("""
            Deliver your reply as a spoken voice message instead of text. \
            Call this ONLY when the user asks to hear the answer in audio/voice \
            ("responde em audio", "me manda por voz", "reply by voice", "fale isso"). \
            Pass the ISO 639-1 language code of the reply you are writing, e.g. "pt" for \
            Portuguese or "en" for English, so the right voice is used. \
            After calling this, write your reply normally as text: it will be synthesized \
            and sent as audio automatically.""")
    public String respondWithVoice(
            @P("ISO 639-1 language code of your spoken reply, e.g. \"pt\" or \"en\"") String language) {
        preference.request(language);
        Log.infof("RespondWithVoiceTool: voice response requested language=%s", language);
        return "Your reply will be delivered as a voice message in " + language + ".";
    }
}
