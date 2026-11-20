package dev.omatheusmesmo.qlawkus.console.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.agent.AgentStream;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.messaging.transcription.VoiceTranscriptionService;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Keeps the live voice sessions by connection id and enforces the per-owner limit at the moment a
 * socket opens. Every session shares one clock for its duration limit and one conversation id, which is
 * the shared conversation when {@code qlawkus.agent.shared-context} is on, the same rule the chat
 * endpoint follows.
 */
@ApplicationScoped
public class VoiceSessions {

    private static final String TOO_MANY_SESSIONS =
            "{\"type\":\"error\",\"code\":\"too-many-sessions\"}";

    private final Map<String, VoiceSession> sessions = new ConcurrentHashMap<>();
    private final VoiceSessionLimiter limiter;
    private final ScheduledExecutorService clock;
    private final VoiceSession.Services services;

    @Inject
    public VoiceSessions(ConsoleVoiceConfig config, AgentConfig agentConfig, AgentStream agent,
            VoiceTranscriptionService transcriber, VoiceSpeech speech, VoiceSessionLimiter limiter,
            ObjectMapper json) {
        this.limiter = limiter;
        this.clock = Executors.newSingleThreadScheduledExecutor(daemon());
        String conversation = agentConfig.sharedContext().enabled() ? ConversationId.SHARED : ConversationId.REST;
        this.services = new VoiceSession.Services(config, conversation, agent, transcriber, speech, limiter,
                json, clock);
    }

    void open(WebSocketConnection connection, String owner) {
        if (!limiter.tryAcquire(owner)) {
            connection.sendTextAndAwait(TOO_MANY_SESSIONS);
            connection.closeAndAwait();
            return;
        }
        VoiceSession session = new VoiceSession(connection.id(), owner, connection, services);
        sessions.put(connection.id(), session);
        session.start();
    }

    void control(String connectionId, String text) {
        VoiceSession session = sessions.get(connectionId);
        if (session != null) {
            session.control(text);
        }
    }

    void audio(String connectionId, byte[] frame) {
        VoiceSession session = sessions.get(connectionId);
        if (session != null) {
            session.audio(frame);
        }
    }

    void closed(String connectionId) {
        VoiceSession session = sessions.remove(connectionId);
        if (session != null) {
            session.close("disconnected", false);
        }
    }

    @PreDestroy
    void shutdown() {
        sessions.values().forEach(session -> session.close("server-shutdown", false));
        sessions.clear();
        clock.shutdownNow();
    }

    private static ThreadFactory daemon() {
        return runnable -> {
            Thread thread = new Thread(runnable, "console-voice-clock");
            thread.setDaemon(true);
            return thread;
        };
    }
}
