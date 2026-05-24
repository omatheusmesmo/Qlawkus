package dev.omatheusmesmo.qlawkus.cognition;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class RespondWithVoiceTool {

    @Inject
    VoiceResponsePreference preference;

    @Tool("""
        IMPORTANT: You MUST CALL this tool (as a function call, NOT by mentioning it in text) \
        whenever the user asks for audio, voice, or spoken output in any language. \
        Examples that require this tool: "send audio", "reply by voice", "speak it", \
        "voice message", "manda audio", "responde por voz", "grava um audio", \
        "mensagem de voz", or any translated equivalent. \
        This works on all messaging platforms (Telegram, Discord, WhatsApp, etc). \
        Pass the ISO 639-1 language code of the reply you are writing, e.g. "pt" for \
        Portuguese or "en" for English, so the right voice is used. \
        After calling this tool, write your reply normally as text: it will be synthesized \
        and sent as audio automatically. Do NOT just describe this tool to the user - CALL IT.""")
    public String respondWithVoice(
        @P("ISO 639-1 language code of your spoken reply, e.g. \"pt\" or \"en\"") String language) {
        boolean ttsEnabled = ConfigProvider.getConfig()
            .getOptionalValue("qlawkus.messaging.tts.enabled", Boolean.class)
            .orElse(false);
        if (!ttsEnabled) {
            Log.warn("RespondWithVoiceTool: called but TTS is disabled, ignoring voice request");
            return "Voice replies are currently unavailable. Reply as text instead and let the user know.";
        }
        preference.request(language);
        Log.infof("RespondWithVoiceTool: voice response requested language=%s", language);
        return "Your reply will be delivered as a voice message in " + language + ".";
    }
}
