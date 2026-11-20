package dev.omatheusmesmo.qlawkus.console.voice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.omatheusmesmo.qlawkus.agent.AgentEvent;
import dev.omatheusmesmo.qlawkus.agent.AgentStream;
import dev.omatheusmesmo.qlawkus.messaging.transcription.VoiceTranscriptionService;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.subscription.Cancellable;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One live voice connection. The browser sends PCM frames bracketed by {@code start} and
 * {@code utterance-end}; each finished utterance becomes a turn: transcribe it, run it through the
 * agent's streamed turn on the shared conversation, cut the reply into sentences and speak them in order.
 *
 * <p>Everything this session sends, and every step of a turn, runs on one worker thread, so the browser
 * receives text, audio and the closing events in the order they were produced. A new utterance or an
 * {@code interrupt} supersedes the active turn: its agent subscription is cancelled, its pending
 * sentences are skipped when they reach the front of the queue, and any event it still emits is
 * dropped before it reaches the socket. Synthesis that is already running cannot be aborted, because
 * the speech adapters are blocking calls, so its result is discarded instead.
 */
final class VoiceSession {

    static final int DEFAULT_SAMPLE_RATE = 48_000;

    private static final int MIN_SAMPLE_RATE = 8_000;
    private static final int MAX_SAMPLE_RATE = 96_000;
    private static final int SENTENCE_LIMIT = 240;

    record Services(ConsoleVoiceConfig config, String conversationId, AgentStream agent,
            VoiceTranscriptionService transcriber, VoiceSpeech speech, VoiceSessionLimiter limiter,
            ObjectMapper json, ScheduledExecutorService clock) {
    }

    private final String connectionId;
    private final String owner;
    private final WebSocketConnection connection;
    private final Services services;
    private final ExecutorService worker;
    private final AtomicInteger turnIds = new AtomicInteger();
    private final AtomicReference<Turn> activeTurn = new AtomicReference<>();
    private final AtomicInteger queuedSpeech = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile UtteranceBuffer utterance;
    private volatile int sampleRate = DEFAULT_SAMPLE_RATE;
    private volatile ScheduledFuture<?> deadline;

    VoiceSession(String connectionId, String owner, WebSocketConnection connection, Services services) {
        this.connectionId = connectionId;
        this.owner = owner;
        this.connection = connection;
        this.services = services;
        this.worker = Executors.newSingleThreadExecutor(daemon("console-voice-" + connectionId));
    }

    void start() {
        deadline = services.clock().schedule(() -> close("session-time-limit", true),
                services.config().maxSessionDuration().toMillis(), TimeUnit.MILLISECONDS);
        submit(() -> sendRaw(ready()));
        Log.infof("Voice session %s opened conversation=%s", connectionId, services.conversationId());
    }

    void control(String text) {
        if (closed.get()) {
            return;
        }
        JsonNode node;
        try {
            node = services.json().readTree(text);
        } catch (JsonProcessingException e) {
            submit(() -> sendRaw(error(null, "invalid-message")));
            return;
        }
        switch (node.path("type").asText("")) {
            case "start" -> startUtterance(node.path("sampleRate").asInt(DEFAULT_SAMPLE_RATE));
            case "utterance-end" -> endUtterance();
            case "interrupt" -> interrupt();
            case "stop" -> close("stopped", true);
            default -> submit(() -> sendRaw(error(null, "unknown-message")));
        }
    }

    void audio(byte[] frame) {
        if (closed.get()) {
            return;
        }
        UtteranceBuffer buffer = utterance;
        if (buffer == null) {
            submit(() -> sendRaw(error(null, "not-listening")));
            return;
        }
        if (frame.length > services.config().maxFrameBytes()) {
            submit(() -> sendRaw(error(null, "frame-too-large")));
            return;
        }
        if (!buffer.append(frame)) {
            utterance = null;
            submit(() -> sendRaw(error(null, "utterance-too-long")));
        }
    }

    void close(String reason, boolean notifyPeer) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Turn previous = activeTurn.getAndSet(null);
        if (previous != null) {
            supersede(previous);
        }
        ScheduledFuture<?> timer = deadline;
        if (timer != null) {
            timer.cancel(false);
        }
        services.limiter().release(owner);
        Log.infof("Voice session %s closed reason=%s", connectionId, reason);
        submit(() -> {
            if (notifyPeer && connection.isOpen()) {
                sendRaw(event("closed", null).put("reason", reason));
            }
            if (connection.isOpen()) {
                connection.closeAndAwait();
            }
        });
        worker.shutdown();
    }

    private void submit(Runnable task) {
        try {
            worker.execute(task);
        } catch (RejectedExecutionException closedSession) {
            Log.debugf("Voice session %s is closed; dropped a task", connectionId);
        }
    }

    private void startUtterance(int rate) {
        if (rate < MIN_SAMPLE_RATE || rate > MAX_SAMPLE_RATE) {
            submit(() -> sendRaw(error(null, "bad-sample-rate")));
            return;
        }
        sampleRate = rate;
        utterance = new UtteranceBuffer(rate, services.config().maxUtteranceBytes(),
                services.config().maxUtteranceDuration());
        submit(() -> sendRaw(event("listening", null)));
    }

    private void endUtterance() {
        UtteranceBuffer buffer = utterance;
        utterance = null;
        if (buffer == null) {
            submit(() -> sendRaw(error(null, "not-listening")));
            return;
        }
        if (buffer.size() == 0) {
            submit(() -> sendRaw(error(null, "empty-utterance")));
            return;
        }
        startTurn(buffer.take());
    }

    private void interrupt() {
        Turn previous = activeTurn.getAndSet(null);
        if (previous != null) {
            supersede(previous);
            submit(() -> sendRaw(event("interrupted", previous.id)));
        }
    }

    private void startTurn(byte[] wav) {
        Turn turn = new Turn(turnIds.incrementAndGet());
        Turn previous = activeTurn.getAndSet(turn);
        if (previous != null) {
            supersede(previous);
            submit(() -> sendRaw(event("interrupted", previous.id)));
        }
        submit(() -> runTurn(turn, wav));
    }

    private void runTurn(Turn turn, byte[] wav) {
        if (!current(turn)) {
            return;
        }
        String text;
        try {
            text = services.transcriber().transcribe(wav, "audio/wav");
        } catch (RuntimeException e) {
            Log.warnf("Voice turn %d transcription failed on %s: %s", turn.id, connectionId,
                    e.getClass().getSimpleName());
            sendRaw(error(turn.id, "transcription-failed"));
            sendRaw(event("turn-done", turn.id));
            return;
        }
        if (!current(turn)) {
            return;
        }
        String utteranceText = text == null ? "" : text.trim();
        sendRaw(event("transcript", turn.id).put("text", utteranceText));
        Log.infof("Voice turn %d transcribed on %s chars=%d", turn.id, connectionId, utteranceText.length());
        if (utteranceText.isEmpty()) {
            sendRaw(event("turn-done", turn.id));
            return;
        }
        Cancellable subscription = services.agent().stream(services.conversationId(), utteranceText)
                .subscribe().with(
                        event -> submit(() -> onAgentEvent(turn, event)),
                        failure -> submit(() -> onAgentFailure(turn, failure)),
                        () -> submit(() -> onAgentComplete(turn)));
        turn.agentSubscription = subscription;
        if (!current(turn)) {
            subscription.cancel();
        }
    }

    private void onAgentEvent(Turn turn, AgentEvent event) {
        if (!current(turn)) {
            return;
        }
        if (event instanceof AgentEvent.Text text) {
            sendRaw(event("text", turn.id).put("chunk", text.chunk()));
            for (String sentence : turn.splitter.accept(text.chunk())) {
                enqueueSpeech(turn, sentence);
            }
        } else if (event instanceof AgentEvent.ToolStart start) {
            sendRaw(event("tool", turn.id).put("phase", "start").put("name", start.name()));
        } else if (event instanceof AgentEvent.ToolEnd end) {
            sendRaw(event("tool", turn.id).put("phase", "end").put("name", end.name()).put("ok", end.ok()));
        }
    }

    private void onAgentComplete(Turn turn) {
        if (!current(turn)) {
            return;
        }
        turn.splitter.flush().ifPresent(rest -> enqueueSpeech(turn, rest));
        submit(() -> {
            if (current(turn)) {
                sendRaw(event("turn-done", turn.id));
            }
        });
    }

    private void onAgentFailure(Turn turn, Throwable failure) {
        if (!current(turn)) {
            return;
        }
        Log.warnf("Voice turn %d agent failed on %s: %s", turn.id, connectionId,
                failure.getClass().getSimpleName());
        sendRaw(error(turn.id, "agent-failed"));
        sendRaw(event("turn-done", turn.id));
    }

    private void enqueueSpeech(Turn turn, String sentence) {
        if (sentence.isBlank()) {
            return;
        }
        if (queuedSpeech.incrementAndGet() > services.config().maxQueuedSegments()) {
            queuedSpeech.decrementAndGet();
            if (turn.queueFullReported.compareAndSet(false, true)) {
                sendRaw(event("notice", turn.id).put("code", "speech-queue-full"));
            }
            return;
        }
        int segment = turn.nextSegment++;
        submit(() -> speak(turn, sentence, segment));
    }

    private void speak(Turn turn, String sentence, int segment) {
        queuedSpeech.decrementAndGet();
        if (!current(turn)) {
            return;
        }
        VoiceSpeech.Speech speech;
        try {
            speech = services.speech().synthesize(sentence);
        } catch (RuntimeException e) {
            Log.warnf("Voice turn %d speech failed on %s: %s", turn.id, connectionId,
                    e.getClass().getSimpleName());
            sendRaw(error(turn.id, "speech-failed"));
            return;
        }
        if (!current(turn)) {
            return;
        }
        sendRaw(event("audio", turn.id).put("segment", segment).put("mimeType", speech.mimeType())
                .put("data", Base64.getEncoder().encodeToString(speech.audio())));
    }

    private boolean current(Turn turn) {
        return !closed.get() && activeTurn.get() == turn && !turn.superseded;
    }

    private static void supersede(Turn turn) {
        turn.superseded = true;
        Cancellable subscription = turn.agentSubscription;
        if (subscription != null) {
            subscription.cancel();
        }
    }

    private ObjectNode ready() {
        return event("ready", null)
                .put("conversation", services.conversationId())
                .put("maxSessionSeconds", services.config().maxSessionDuration().toSeconds())
                .put("maxUtteranceSeconds", services.config().maxUtteranceDuration().toSeconds())
                .put("hangoverMs", services.config().hangover().toMillis())
                .put("speechThreshold", services.config().speechThreshold())
                .put("transcription", services.transcriber().engine())
                .put("speech", services.speech().engine())
                .put("speechBuffered", services.speech().buffered());
    }

    private ObjectNode error(Integer turn, String code) {
        return event("error", turn).put("code", code);
    }

    private ObjectNode event(String type, Integer turn) {
        ObjectNode node = services.json().createObjectNode().put("type", type);
        if (turn != null) {
            node.put("turn", turn);
        }
        return node;
    }

    private void sendRaw(ObjectNode node) {
        if (!connection.isOpen()) {
            return;
        }
        try {
            connection.sendTextAndAwait(services.json().writeValueAsString(node));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize a voice event", e);
        } catch (RuntimeException e) {
            Log.debugf("Voice send failed on %s: %s", connectionId, e.getClass().getSimpleName());
        }
    }

    private static ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class Turn {

        private final int id;
        private final SentenceSplitter splitter = new SentenceSplitter(SENTENCE_LIMIT);
        private final AtomicBoolean queueFullReported = new AtomicBoolean();
        private volatile boolean superseded;
        private volatile Cancellable agentSubscription;
        private int nextSegment = 1;

        private Turn(int id) {
            this.id = id;
        }
    }
}
