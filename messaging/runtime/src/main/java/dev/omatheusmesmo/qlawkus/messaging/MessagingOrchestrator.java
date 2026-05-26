package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.agent.QlawkusAgentWorkflow;
import dev.omatheusmesmo.qlawkus.cognition.ConversationControl;
import dev.omatheusmesmo.qlawkus.cognition.VoiceResponsePreference;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import dev.omatheusmesmo.qlawkus.messaging.auth.MessagingAuthService;
import dev.omatheusmesmo.qlawkus.messaging.transcription.VoiceTranscriptionService;
import dev.omatheusmesmo.qlawkus.messaging.tts.TtsRouter;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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
    Instance<QlawkusAgentWorkflow> workflowInstance;

    @Inject
    Instance<AgentDeliveryContext> deliveryContextInstance;

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

    @ConfigProperty(name = "qlawkus.messaging.tts.default-language", defaultValue = "en")
    String defaultTtsLanguage;

    @ConfigProperty(name = "qlawkus.agent.shared-context.enabled", defaultValue = "true")
    boolean sharedContextEnabled;

    @ConfigProperty(name = "qlawkus.agent.context-ttl-minutes", defaultValue = "60")
    long contextTtlMinutes;

    @ConfigProperty(name = "qlawkus.agent.agentic-workflow.enabled", defaultValue = "true")
    boolean agenticWorkflowEnabled;

    public Uni<Void> process(MessagingMessage message) {
        Log.infof("MessagingOrchestrator: received provider=%s userId=%s chatId=%s textLen=%d hasAudio=%s",
                message.providerId(), message.userId(), message.chatId(),
                message.text() != null ? message.text().length() : 0,
                message.audio().isPresent());

        if (!authService.isAuthorized(message)) {
            Log.warnf("MessagingOrchestrator: unauthorized userId=%s provider=%s",
                    message.userId(), message.providerId());
            return Uni.createFrom().voidItem();
        }

        long startMs = System.currentTimeMillis();
        return Uni.createFrom()
                .item(() -> resolveText(message))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .invoke(text -> Log.infof("MessagingOrchestrator: invoking agent userId=%s textLen=%d",
                        message.userId(), text != null ? text.length() : 0))
                .map(text -> {
                    setDeliveryContext(memoryId(message), message.providerId(), message.chatId());
                    return runAgentWithContext(memoryId(message), text);
                })
                .invoke(result -> Log.infof(
                        "MessagingOrchestrator: agent done userId=%s elapsedMs=%d responseLen=%d voice=%s",
                        message.userId(), System.currentTimeMillis() - startMs,
                        result.text() != null ? result.text().length() : 0, result.voiceRequested()))
                .onFailure().invoke(err -> Log.errorf(err,
                        "MessagingOrchestrator: processing failed userId=%s elapsedMs=%d",
                        message.userId(), System.currentTimeMillis() - startMs))
                .onFailure().recoverWithItem(err -> AgentResult.textOnly(
                        "⚠️ Failed to process your message: " + summarizeError(err)))
                .flatMap(result -> deliver(message, result))
                .invoke(() -> Log.infof("MessagingOrchestrator: response delivered userId=%s totalMs=%d",
                        message.userId(), System.currentTimeMillis() - startMs));
    }

    private Uni<Void> deliver(MessagingMessage message, AgentResult result) {
        if (result.voiceRequested()) {
            if (!ttsRouter.enabled()) {
                Log.warnf("MessagingOrchestrator: voice requested but TTS is disabled (qlawkus.messaging.tts.enabled=false), falling back to text for userId=%s", message.userId());
            } else {
                return Uni.createFrom()
                    .item(() -> ttsRouter.synthesize(stripForSpeech(result.text()), result.language()))
                    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                    .flatMap(audio -> notificationService.sendVoice(
                        message.providerId(), message.chatId(), audio,
                        voiceFilename(result.text()), result.text()))
                    .onFailure().invoke(err -> Log.errorf(err,
                        "MessagingOrchestrator: voice synthesis/send failed userId=%s, falling back to text",
                        message.userId()))
                    .onFailure().recoverWithUni(() -> notificationService.send(
                        message.providerId(), message.chatId(), result.text()));
            }
        }
        return notificationService.send(message.providerId(), message.chatId(), result.text());
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
        return sharedContextEnabled
                ? ConversationId.SHARED
                : message.providerId() + ":" + message.chatId();
    }

    private AgentResult runAgentWithContext(String conversationId, String text) {
        ManagedContext requestContext = Arc.container() != null
                ? Arc.container().requestContext() : null;
        boolean activated = false;
        if (requestContext != null && !requestContext.isActive()) {
            requestContext.activate();
            activated = true;
        }
        if (detectVoiceIntent(text) && ttsRouter.enabled()) {
            voicePreference.request(defaultTtsLanguage);
            Log.infof("MessagingOrchestrator: auto-detected voice intent from user message, default language=%s (LLM may override)", defaultTtsLanguage);
        }
        AgentResult result;
        boolean clearRequested;
        try {
            expireIdleConversation(conversationId);
            String response = invokeAgent(conversationId, text);
            clearRequested = conversationControl.isClearRequested();
            result = new AgentResult(response, voicePreference.isRequested(), voicePreference.language());
        } finally {
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

    private String invokeAgent(String conversationId, String text) {
        if (agenticWorkflowEnabled && !workflowInstance.isUnsatisfied()) {
            try {
                String response = workflowInstance.get().invoke(conversationId, text);
                Log.debugf("MessagingOrchestrator: agentic workflow completed for conversationId=%s", conversationId);
                return response;
            } catch (Exception e) {
                Log.warnf(e, "MessagingOrchestrator: agentic workflow failed, falling back to AgentService");
            }
        }
        return agentService.chatSync(conversationId, text);
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

    private String resolveText(MessagingMessage message) {
        return message.audio().map(audio -> {
            try {
                return transcriptionService.transcribe(audio);
            } catch (Exception e) {
                Log.warnf(e, "MessagingOrchestrator: transcription failed for userId=%s, using text fallback",
                        message.userId());
                return message.text();
            }
        }).orElse(message.text());
    }
}
