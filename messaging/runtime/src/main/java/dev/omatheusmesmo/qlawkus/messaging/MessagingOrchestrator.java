package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.agent.ToolProgress;
import dev.omatheusmesmo.qlawkus.cognition.ConversationControl;
import dev.omatheusmesmo.qlawkus.cognition.VoiceResponsePreference;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.messaging.auth.MessagingAuthService;
import dev.omatheusmesmo.qlawkus.messaging.progress.ToolProgressReporter;
import dev.omatheusmesmo.qlawkus.messaging.progress.ToolProgressRouter;
import dev.omatheusmesmo.qlawkus.messaging.progress.ToolProgressSettings;
import dev.omatheusmesmo.qlawkus.messaging.progress.ToolProgressTurn;
import dev.omatheusmesmo.qlawkus.messaging.transcription.VoiceTranscriptionService;
import dev.omatheusmesmo.qlawkus.messaging.tts.TtsConfig;
import dev.omatheusmesmo.qlawkus.messaging.tts.TtsRouter;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

@ApplicationScoped
public class MessagingOrchestrator {

    private static final String EMOJI_REGEX =
            "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2190}-\\x{21FF}"
            + "\\x{2B00}-\\x{2BFF}\\x{FE00}-\\x{FE0F}\\x{1F1E6}-\\x{1F1FF}"
            + "\\x{200D}\\x{2122}\\x{2139}\\x{20E3}]";

    @Inject
    AgentService agentService;

    @Inject
    Instance<AgentRunner> agentRunnerInstance;

    @Inject
    Instance<AgentDeliveryContext> deliveryContextInstance;

    @Inject
    ProviderRegistry providerRegistry;

    @Inject
    ToolProgressSettings toolProgress;

    @Inject
    ToolProgressRouter progressRouter;

    @Inject
    MessagingAuthService authService;

    @Inject
    NotificationService notificationService;

    @Inject
    VoiceTranscriptionService transcriptionService;

    @Inject
    VoiceResponsePreference voicePreference;

    @Inject
    ConversationControl conversationControl;

    @Inject
    WorkingMemoryStore workingMemoryStore;

    @Inject
    TtsRouter ttsRouter;

    @Inject
    TtsConfig ttsConfig;

    @Inject
    AgentConfig agentConfig;

    public Uni<Void> process(MessagingMessage message) {
        String channel = message.providerId();
        if (!authService.isAuthorized(message)) {
            MessagingLog.warn(channel, null, "Message rejected provider=%s userId=%s reason=allowlist",
                    channel, message.userId());
            return Uni.createFrom().voidItem();
        }

        String conversation = memoryId(message);
        MessagingLog.info(channel, conversation, "Message accepted provider=%s chatId=%s userId=%s kind=%s length=%d",
                channel, message.chatId(), message.userId(), kindOf(message), lengthOf(message));

        if (ToolProgressSettings.isVerboseCommand(message.text())) {
            return notificationService.send(channel, message.chatId(), cycleToolProgress(message), conversation);
        }

        long startMs = System.currentTimeMillis();
        return Uni.createFrom()
                .item(() -> resolveText(message, conversation))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .map(text -> {
                    setDeliveryContext(conversation, channel, message.chatId());
                    return runAgentWithContext(conversation, text, message);
                })
                .invoke(result -> Log.debugf(
                        "MessagingOrchestrator: agent done userId=%s elapsedMs=%d responseLen=%d voice=%s",
                        message.userId(), System.currentTimeMillis() - startMs,
                        result.text() != null ? result.text().length() : 0, result.voiceRequested()))
                .onFailure().invoke(err -> Log.errorf(err,
                        "MessagingOrchestrator: processing failed userId=%s elapsedMs=%d",
                        message.userId(), System.currentTimeMillis() - startMs))
                .onFailure().recoverWithItem(err -> AgentResult.textOnly(
                        "⚠️ Failed to process your message: " + summarizeError(err)))
                .flatMap(result -> deliver(message, result, conversation));
    }

    private static String kindOf(MessagingMessage message) {
        return message.audio().isPresent() ? "voice" : "text";
    }

    private static long lengthOf(MessagingMessage message) {
        return message.audio().map(audio -> (long) audio.length)
                .orElseGet(() -> message.text() != null ? message.text().length() : 0L);
    }

    private Uni<Void> deliver(MessagingMessage message, AgentResult result, String conversation) {
        String channel = message.providerId();
        if (result.voiceRequested()) {
            if (!ttsRouter.enabled()) {
                MessagingLog.warn(channel, conversation,
                        "Voice requested but TTS is disabled (qlawkus.messaging.tts.enabled=false), replying with text");
            } else {
                return Uni.createFrom()
                    .item(() -> MessagingLog.within(channel, conversation,
                            () -> ttsRouter.synthesize(stripForSpeech(result.text()), result.language())))
                    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                    .flatMap(audio -> notificationService.sendVoice(
                        channel, message.chatId(), audio,
                        voiceFilename(result.text()), result.text(), conversation))
                    .onFailure().recoverWithUni(() -> notificationService.send(
                        channel, message.chatId(), result.text(), conversation));
            }
        }
        return notificationService.send(channel, message.chatId(), result.text(), conversation);
    }

    private String voiceFilename(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", " ");
        StringBuilder slug = new StringBuilder();
        for (String word : normalized.trim().split("\\s+")) {
            if (word.isBlank()) {
                continue;
            }
            if (slug.length() > 0) {
                slug.append('-');
            }
            slug.append(word);
            if (slug.length() >= 40) {
                break;
            }
        }
        String result = slug.toString().replaceAll("-+", "-").replaceAll("^-|-$", "");
        return (result.isBlank() ? "resposta" : result) + ".mp3";
    }

    private String stripForSpeech(String text) {
        String cleaned = text
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("[*_#>]", "")
                .replaceAll(EMOJI_REGEX, "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        int cap = 2000;
        return cleaned.length() > cap ? cleaned.substring(0, cap) : cleaned;
    }

    private String summarizeError(Throwable err) {
        Throwable root = err;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String type = root.getClass().getSimpleName();
        String msg = root.getMessage();
        if (msg == null) {
            return type;
        }
        String trimmed = msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
        return type + ": " + trimmed;
    }

    private void expireIdleConversation(String conversationId) {
        long contextTtlMinutes = agentConfig.contextTtlMinutes();
        if (contextTtlMinutes <= 0) {
            return;
        }
        Instant last = workingMemoryStore.lastActivity(conversationId);
        if (last != null && Duration.between(last, Instant.now()).toMinutes() >= contextTtlMinutes) {
            workingMemoryStore.deleteMessages(conversationId);
            Log.infof("MessagingOrchestrator: conversation id=%s idle >= %d min, resetting context",
                    conversationId, contextTtlMinutes);
        }
    }

    private String memoryId(MessagingMessage message) {
        return agentConfig.sharedContext().enabled()
                ? ConversationId.SHARED
                : message.providerId() + ":" + message.chatId();
    }

    /**
     * Cycles the tool-progress mode of the message's chat. Checks the allowlist itself, because the
     * Discord slash command reaches it without going through {@link #process}.
     */
    public String cycleToolProgress(MessagingMessage message) {
        if (!authService.isAuthorized(message)) {
            MessagingLog.warn(message.providerId(), null, "Command rejected provider=%s userId=%s reason=allowlist",
                    message.providerId(), message.userId());
            return "Not authorized.";
        }
        ToolProgress mode = toolProgress.cycle(ToolProgressSettings.key(message.providerId(), message.chatId()));
        return "Tool progress for this chat: " + mode.wireName();
    }

    private AgentResult runAgentWithContext(String conversationId, String text, MessagingMessage message) {
        ManagedContext requestContext = Arc.container() != null
                ? Arc.container().requestContext() : null;
        boolean activated = false;
        if (requestContext != null && !requestContext.isActive()) {
            requestContext.activate();
            activated = true;
        }
        if (detectVoiceIntent(text) && ttsRouter.enabled()) {
            String defaultTtsLanguage = ttsConfig.defaultLanguage();
            voicePreference.request(defaultTtsLanguage);
            Log.infof("MessagingOrchestrator: auto-detected voice intent from user message, default language=%s (LLM may override)", defaultTtsLanguage);
        }
        AgentResult result;
        boolean clearRequested;
        try {
            expireIdleConversation(conversationId);
            String response = invokeAgent(conversationId, text, message);
            clearRequested = conversationControl.isClearRequested();
            result = new AgentResult(response, voicePreference.isRequested(), voicePreference.language());
        } finally {
            if (deliveryContextInstance.isResolvable()) {
                deliveryContextInstance.get().clear();
            }
            if (activated) {
                requestContext.terminate();
            }
        }
        if (clearRequested) {
            workingMemoryStore.deleteMessages(conversationId);
            Log.infof("MessagingOrchestrator: cleared conversation memory id=%s", conversationId);
        }
        return result;
    }

    private String invokeAgent(String conversationId, String text, MessagingMessage message) {
        ToolProgressTurn turn = progressRouter.begin(conversationId, progressReporter(message));
        try {
            if (!agentRunnerInstance.isUnsatisfied()) {
                try {
                    String response = agentRunnerInstance.get().chat(conversationId, text);
                    Log.debugf("MessagingOrchestrator: agent completed for conversationId=%s", conversationId);
                    return response;
                } catch (Exception e) {
                    Log.warnf(e, "MessagingOrchestrator: agent failed, falling back to AgentService");
                }
            }
            return agentService.chatSync(conversationId, text);
        } finally {
            progressRouter.end(conversationId, turn);
            turn.finish(toolProgress.completion());
        }
    }

    private ToolProgressReporter progressReporter(MessagingMessage message) {
        ToolProgress mode = toolProgress.modeFor(ToolProgressSettings.key(message.providerId(), message.chatId()));
        if (!mode.showsTools()) {
            return null;
        }
        return providerRegistry.getProvider(message.providerId())
                .flatMap(MessagingProvider::progressSink)
                .map(sink -> ToolProgressReporter.forChat(mode, sink, message.chatId(), toolProgress.maxLines()))
                .orElse(null);
    }

    private void setDeliveryContext(String memoryId, String providerId, String chatId) {
        if (!deliveryContextInstance.isUnsatisfied()) {
            deliveryContextInstance.get().set(memoryId, providerId, chatId);
        }
    }

    private static final Pattern VOICE_INTENT_PATTERN = Pattern.compile(
        "(?i)\\b(?:audio|voice|áudio|voz)\\b"
        + "|voice\\s*(?:message|note|reply|response)"
        + "|(?:mensagem|nota|resposta)\\s*(?:de|por)?\\s*(?:voz|áudio)"
        + "|\\b(?:send|give|reply|respond|record|speak).{0,10}(?:audio|voice)\\b"
        + "|\\b(?:manda|envia|grava|fala|responde|responda|enviar|mandar|gravar|falar).{0,10}(?:áudio|audio|voz|voice)\\b"
    );

    private boolean detectVoiceIntent(String text) {
        return text != null && VOICE_INTENT_PATTERN.matcher(text).find();
    }

    private record AgentResult(String text, boolean voiceRequested, String language) {
        static AgentResult textOnly(String text) {
            return new AgentResult(text, false, null);
        }
    }

    private String resolveText(MessagingMessage message, String conversation) {
        return message.audio().map(audio -> transcribe(message, conversation, audio))
                .orElse(message.text());
    }

    private String transcribe(MessagingMessage message, String conversation, byte[] audio) {
        String channel = message.providerId();
        long startNanos = System.nanoTime();
        String engine = transcriptionService.engine();
        try {
            String transcript = transcriptionService.transcribe(audio);
            MessagingLog.info(channel, conversation,
                    "Voice transcribed engine=%s audioBytes=%d elapsedMs=%d outcome=ok",
                    engine, audio.length, elapsedMs(startNanos));
            return transcript;
        } catch (Exception e) {
            MessagingLog.warn(channel, conversation,
                    "Voice transcription failed engine=%s audioBytes=%d elapsedMs=%d outcome=error exception=%s, using text fallback",
                    engine, audio.length, elapsedMs(startNanos), MessagingLog.exceptionName(e));
            return message.text();
        }
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
