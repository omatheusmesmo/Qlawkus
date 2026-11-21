package dev.omatheusmesmo.qlawkus.console.voice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.omatheusmesmo.qlawkus.agent.AgentEvent;
import dev.omatheusmesmo.qlawkus.agent.AgentStream;
import dev.omatheusmesmo.qlawkus.messaging.transcription.VoiceTranscriptionService;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.subscription.Cancellable;
import java.util.Base64;
import java.util.Optional;
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
 * {@code utterance-end}, or a typed message; each finished utterance becomes a turn: transcribe it, run it
 * through the agent's streamed turn on the shared conversation, cut the reply into sentences and speak them
 * in order, each in the language the session chose for it.
 *
 * <p>Everything this session sends, and every step of a turn, runs on one worker thread, so the browser
 * receives text, audio and the closing events in the order they were produced. A new utterance, a typed
 * message or an {@code interrupt} supersedes the active turn: its agent subscription is cancelled, its
 * pending sentences are skipped when they reach the front of the queue, and any event it still emits is
 * dropped before it reaches the socket. Synthesis that is already running cannot be aborted, because the
 * speech adapters are blocking calls, so its result is discarded instead.
 */
final class VoiceSession {

    static final int DEFAULT_SAMPLE_RATE = 48_000;

    private static final int MIN_SAMPLE_RATE = 8_000;
    private static final int MAX_SAMPLE_RATE = 96_000;
    private static final int SENTENCE_LIMIT = 240;
    private static final int REPLY_CONTEXT = 1_000;

    record Services(ConsoleVoiceConfig config, String conversationId, AgentStream agent,
            VoiceTranscriptionService transcriber, VoiceSpeech speech, VoiceLanguages languages,
            String initialLanguage, VoiceSessionLimiter limiter, ObjectMapper json,
            ScheduledExecutorService clock) {
    }

    private final String connectionId;
    private final String owner;
    private final WebSocketConnection connection;
    private final Services services;
    private final ExecutorService worker;
    private final LanguageTracker tracker = new LanguageTracker();
    private final AtomicInteger turnIds = new AtomicInteger();
    private final AtomicReference<Turn> activeTurn = new AtomicReference<>();
    private final AtomicInteger queuedSpeech = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile UtteranceBuffer utterance;
    private volatile int sampleRate = DEFAULT_SAMPLE_RATE;
    private volatile ScheduledFuture<?> deadline;
    private volatile String language;

    VoiceSession(String connectionId, String owner, WebSocketConnection connection, Services services) {
        this.connectionId = connectionId;
        this.owner = owner;
        this.connection = connection;
        this.services = services;
        this.language = services.initialLanguage();
        this.worker = Executors.newSingleThreadExecutor(daemon("console-voice-" + connectionId));
    }

    void start() {
        deadline = services.clock().schedule(() -> close("session-time-limit", true),
                services.config().maxSessionDuration().toMillis(), TimeUnit.MILLISECONDS);
        submit(() -> sendRaw(ready()));
        Log.infof("Voice session %s opened conversation=%s language=%s", connectionId,
                services.conversationId(), language);
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
            case "language" -> chooseLanguage(node.path("language").asText(""));
            case "text" -> typedMessage(node.path("text").asText(""));
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

    private void chooseLanguage(String raw) {
        Optional<String> choice = VoiceLanguages.choice(raw).filter(services.languages()::accepts);
        if (choice.isEmpty()) {
            submit(() -> sendRaw(error(null, "unknown-language")));
            return;
        }
        language = choice.get();
        Log.infof("Voice session %s language=%s", connectionId, language);
        submit(() -> sendRaw(event("language", null).put("language", language)));
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
        String hint = services.languages().sttHint(language).orElse(null);
        startTurn(buffer.take(), hint);
    }

    private void typedMessage(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            submit(() -> sendRaw(error(null, "empty-text")));
            return;
        }
        if (text.length() > services.config().maxTextChars()) {
            submit(() -> sendRaw(error(null, "text-too-long")));
            return;
        }
        utterance = null;
        Turn turn = newTurn();
        submit(() -> runTypedTurn(turn, text));
    }

    private void interrupt() {
        Turn previous = activeTurn.getAndSet(null);
        if (previous != null) {
            supersede(previous);
            submit(() -> sendRaw(event("interrupted", previous.id)));
        }
    }

    private void startTurn(byte[] wav, String hint) {
        Turn turn = newTurn();
        submit(() -> runTurn(turn, wav, hint));
    }

    private Turn newTurn() {
        Turn turn = new Turn(turnIds.incrementAndGet());
        Turn previous = activeTurn.getAndSet(turn);
        if (previous != null) {
            supersede(previous);
            submit(() -> sendRaw(event("interrupted", previous.id)));
        }
        return turn;
    }

    private void runTurn(Turn turn, byte[] wav, String hint) {
        if (!current(turn)) {
            return;
        }
        VoiceTranscriptionService.Transcription heard;
        try {
            heard = services.transcriber().transcribe(wav, "audio/wav", hint);
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
        String utteranceText = heard.text() == null ? "" : heard.text().trim();
        String detected = heard.language();
        if (detected != null && services.languages().speakable(detected)) {
            tracker.observe(detected);
        }
        sendRaw(event("transcript", turn.id).put("text", utteranceText).put("language", detected)
                .put("sessionLanguage", tracker.stable().orElse(null)));
        Log.infof("Voice turn %d transcribed on %s chars=%d language=%s", turn.id, connectionId,
                utteranceText.length(), detected == null ? "unknown" : detected);
        if (utteranceText.isEmpty()) {
            sendRaw(event("turn-done", turn.id));
            return;
        }
        answer(turn, utteranceText);
    }

    private void runTypedTurn(Turn turn, String text) {
        if (!current(turn)) {
            return;
        }
        sendRaw(event("transcript", turn.id).put("text", text).put("typed", true));
        Log.infof("Voice turn %d typed on %s chars=%d", turn.id, connectionId, text.length());
        answer(turn, text);
    }

    private void answer(Turn turn, String text) {
        Cancellable subscription = services.agent().stream(services.conversationId(), text)
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
            turn.recordReply(text.chunk(), REPLY_CONTEXT);
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
        String replyLanguage = services.languages().replyLanguage(language, tracker.stable(), turn.replyText());
        int segment = turn.nextSegment++;
        submit(() -> speak(turn, sentence, replyLanguage, segment));
    }

    private void speak(Turn turn, String sentence, String replyLanguage, int segment) {
        queuedSpeech.decrementAndGet();
        if (!current(turn)) {
            return;
        }
        VoiceSpeech.Speech speech;
        try {
            speech = services.speech().synthesize(sentence, replyLanguage);
        } catch (RuntimeException e) {
            Log.warnf("Voice turn %d speech failed on %s: %s", turn.id, connectionId,
                    e.getClass().getSimpleName());
            sendRaw(error(turn.id, "speech-failed"));
            return;
        }
        if (!current(turn)) {
            return;
        }
        sendRaw(event("audio", turn.id).put("segment", segment).put("language", replyLanguage)
                .put("mimeType", speech.mimeType())
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
        ObjectNode node = event("ready", null)
                .put("conversation", services.conversationId())
                .put("maxSessionSeconds", services.config().maxSessionDuration().toSeconds())
                .put("maxUtteranceSeconds", services.config().maxUtteranceDuration().toSeconds())
                .put("maxTextChars", services.config().maxTextChars())
                .put("hangoverMs", services.config().hangover().toMillis())
                .put("speechThreshold", services.config().speechThreshold())
                .put("transcription", services.transcriber().engine())
                .put("speech", services.speech().engine())
                .put("speechBuffered", services.speech().buffered())
                .put("language", language)
                .put("defaultLanguage", services.languages().fallback());
        ArrayNode offered = node.putArray("languages");
        services.languages().offered().forEach(offered::add);
        return node;
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
        private final StringBuilder reply = new StringBuilder();
        private volatile boolean superseded;
        private volatile Cancellable agentSubscription;
        private int nextSegment = 1;

        private Turn(int id) {
            this.id = id;
        }

        private void recordReply(String chunk, int limit) {
            reply.append(chunk);
            if (reply.length() > limit) {
                reply.delete(0, reply.length() - limit);
            }
        }

        private String replyText() {
            return reply.toString();
        }
    }
}
