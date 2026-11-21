package dev.omatheusmesmo.qlawkus.console.voice;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The voice page: a server-rendered shell whose script opens the live socket, captures the microphone
 * and plays the replies. The page holds no conversation state of its own; the socket shares the chat's
 * conversation.
 */
@Path("/console/voice")
@Authenticated
@Produces(MediaType.TEXT_HTML)
public class VoiceConsoleResource {

    private final Template voiceTemplate;
    private final ConsoleVoiceConfig config;

    public VoiceConsoleResource(@Location("console/voice.html") Template voiceTemplate, ConsoleVoiceConfig config) {
        this.voiceTemplate = voiceTemplate;
        this.config = config;
    }

    @GET
    public TemplateInstance page() {
        return voiceTemplate.data("active", "voice")
                .data("socketPath", VoiceSocket.PATH)
                .data("hangoverMs", config.hangover().toMillis())
                .data("speechThreshold", config.speechThreshold())
                .data("maxUtteranceMs", config.maxUtteranceDuration().toMillis())
                .data("maxTextChars", config.maxTextChars());
    }
}
