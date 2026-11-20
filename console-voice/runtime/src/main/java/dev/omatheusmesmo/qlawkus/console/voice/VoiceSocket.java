package dev.omatheusmesmo.qlawkus.console.voice;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.OnBinaryMessage;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

/**
 * The live voice socket. It sits behind the same Basic credentials as the rest of the console, and the
 * {@link VoiceOriginCheck} upgrade check refuses any origin that is not the console's own. The owner is
 * the authenticated principal, so the per-owner session limit counts the one person this agent serves.
 */
@Authenticated
@WebSocket(path = VoiceSocket.PATH, endpointId = VoiceSocket.ENDPOINT_ID)
public class VoiceSocket {

    public static final String PATH = "/console/voice/ws";
    public static final String ENDPOINT_ID = "console-voice";

    private final SecurityIdentity identity;
    private final VoiceSessions sessions;

    @Inject
    public VoiceSocket(SecurityIdentity identity, VoiceSessions sessions) {
        this.identity = identity;
        this.sessions = sessions;
    }

    @OnOpen
    void open(WebSocketConnection connection) {
        sessions.open(connection, owner());
    }

    @OnTextMessage
    void control(WebSocketConnection connection, String message) {
        sessions.control(connection.id(), message);
    }

    @OnBinaryMessage
    void audio(WebSocketConnection connection, byte[] frame) {
        sessions.audio(connection.id(), frame);
    }

    @OnClose
    void closed(WebSocketConnection connection) {
        sessions.closed(connection.id());
    }

    private String owner() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }
}
