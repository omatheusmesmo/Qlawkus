package dev.omatheusmesmo.qlawkus.it.consolevoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import io.quarkus.websockets.next.WebSocketClientConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A browser stand-in for the voice socket: it records every JSON event in arrival order and waits for them
 * by type, so a test can assert the order events arrived in, which is the property under test. The handler
 * runs non-blocking on the connection's event loop: on the default worker pool two messages sent back to back
 * can be recorded out of order, which would make a correct server look wrong.
 */
final class VoiceClient implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long TIMEOUT_SECONDS = 20;
    private static final int FRAME_BYTES = 2000;

    private WebSocketClientConnection connection;
    private final BlockingQueue<JsonNode> incoming = new LinkedBlockingQueue<>();
    private final List<JsonNode> received = Collections.synchronizedList(new ArrayList<>());

    private VoiceClient() {
    }

    static VoiceClient open(BasicWebSocketConnector connector, URI base, String origin, String authorization) {
        BasicWebSocketConnector configured = connector.baseUri(base).path("/console/voice/ws");
        if (origin != null) {
            configured = configured.addHeader("Origin", origin);
        }
        if (authorization != null) {
            configured = configured.addHeader("Authorization", authorization);
        }
        VoiceClient client = new VoiceClient();
        client.connection = configured
                .executionModel(BasicWebSocketConnector.ExecutionModel.NON_BLOCKING)
                .onTextMessage((c, message) -> client.record(message))
                .connectAndAwait();
        return client;
    }

    static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private void record(String message) {
        try {
            JsonNode event = JSON.readTree(message);
            received.add(event);
            incoming.add(event);
        } catch (Exception e) {
            throw new IllegalStateException("not a JSON voice event: " + message, e);
        }
    }

    void sendJson(String json) {
        connection.sendTextAndAwait(json);
    }

    void sendFrame(byte[] pcm) {
        connection.sendBinaryAndAwait(pcm);
    }

    void speakUtterance(int bytes) {
        sendJson("{\"type\":\"start\",\"sampleRate\":16000}");
        int sent = 0;
        while (sent < bytes) {
            int frame = Math.min(FRAME_BYTES, bytes - sent);
            sendFrame(new byte[frame]);
            sent += frame;
        }
        sendJson("{\"type\":\"utterance-end\"}");
    }

    JsonNode awaitType(String type) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            JsonNode event;
            try {
                event = incoming.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            if (event != null && type.equals(event.path("type").asText())) {
                return event;
            }
        }
        throw new AssertionError("no '" + type + "' event within " + TIMEOUT_SECONDS + "s; seen: " + snapshot());
    }

    JsonNode awaitTypeWhere(String type, Predicate<JsonNode> matches) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            for (JsonNode event : snapshot()) {
                if (type.equals(event.path("type").asText()) && matches.test(event)) {
                    return event;
                }
            }
            sleep();
        }
        throw new AssertionError("no matching '" + type + "' event; seen: " + snapshot());
    }

    JsonNode nextEvent() {
        try {
            JsonNode event = incoming.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (event == null) {
                throw new AssertionError("no event within " + TIMEOUT_SECONDS + "s");
            }
            return event;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    List<JsonNode> snapshot() {
        synchronized (received) {
            return List.copyOf(received);
        }
    }

    boolean isOpen() {
        return connection.isOpen();
    }

    void assertAllOfTypeAbsent(String type, Predicate<JsonNode> matches) {
        for (JsonNode event : snapshot()) {
            assertTrue(!(type.equals(event.path("type").asText()) && matches.test(event)),
                    "unexpected '" + type + "' event: " + event);
        }
    }

    static void assertOrdered(List<JsonNode> events, String first, String second) {
        int a = indexOf(events, first);
        int b = indexOf(events, second);
        assertTrue(a >= 0 && b >= 0 && a < b, first + " must precede " + second + " in " + events);
    }

    static int indexOf(List<JsonNode> events, String type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.equals(events.get(i).path("type").asText())) {
                return i;
            }
        }
        return -1;
    }

    static void assertCode(JsonNode event, String code) {
        assertEquals(code, event.path("code").asText(), event.toString());
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        if (connection.isOpen()) {
            connection.closeAndAwait();
        }
    }
}
