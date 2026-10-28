package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.agent.AgentDeliveryContext;
import dev.omatheusmesmo.qlawkus.agent.AgentRunner;
import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.agent.ConversationId;
import dev.omatheusmesmo.qlawkus.agent.ToolProgress;
import dev.omatheusmesmo.qlawkus.cognition.ConversationControl;
import dev.omatheusmesmo.qlawkus.cognition.VoiceResponsePreference;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.messaging.auth.MessagingAuthService;
import dev.omatheusmesmo.qlawkus.messaging.logging.MessagingLogCapture;
import dev.omatheusmesmo.qlawkus.messaging.progress.ToolProgressRouter;
import dev.omatheusmesmo.qlawkus.messaging.progress.ToolProgressSettings;
import dev.omatheusmesmo.qlawkus.messaging.transcription.VoiceTranscriptionService;
import dev.omatheusmesmo.qlawkus.messaging.tts.TtsConfig;
import dev.omatheusmesmo.qlawkus.messaging.tts.TtsRouter;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessagingOrchestratorLogTest {

    private MessagingOrchestrator orchestrator;
    private AgentService agentService;
    private MessagingAuthService authService;
    private NotificationService notificationService;
    private VoiceTranscriptionService transcriptionService;

    @BeforeEach
    void setUp() {
        orchestrator = new MessagingOrchestrator();
        agentService = Mockito.mock(AgentService.class);
        authService = Mockito.mock(MessagingAuthService.class);
        notificationService = Mockito.mock(NotificationService.class);
        transcriptionService = Mockito.mock(VoiceTranscriptionService.class);
        VoiceResponsePreference voicePreference = Mockito.mock(VoiceResponsePreference.class);
        ConversationControl conversationControl = Mockito.mock(ConversationControl.class);
        WorkingMemoryStore workingMemoryStore = Mockito.mock(WorkingMemoryStore.class);

        orchestrator.agentService = agentService;
        orchestrator.authService = authService;
        orchestrator.notificationService = notificationService;
        orchestrator.transcriptionService = transcriptionService;
        orchestrator.voicePreference = voicePreference;
        orchestrator.conversationControl = conversationControl;
        orchestrator.workingMemoryStore = workingMemoryStore;
        orchestrator.ttsRouter = Mockito.mock(TtsRouter.class);
        orchestrator.progressRouter = new ToolProgressRouter();
        orchestrator.providerRegistry = Mockito.mock(ProviderRegistry.class);

        ToolProgressSettings toolProgress = Mockito.mock(ToolProgressSettings.class);
        when(toolProgress.modeFor(anyString())).thenReturn(ToolProgress.OFF);
        orchestrator.toolProgress = toolProgress;

        TtsConfig ttsConfig = Mockito.mock(TtsConfig.class);
        when(ttsConfig.defaultLanguage()).thenReturn("en");
        orchestrator.ttsConfig = ttsConfig;

        AgentConfig agentConfig = Mockito.mock(AgentConfig.class);
        AgentConfig.SharedContext sharedContext = Mockito.mock(AgentConfig.SharedContext.class);
        when(sharedContext.enabled()).thenReturn(true);
        when(agentConfig.sharedContext()).thenReturn(sharedContext);
        when(agentConfig.contextTtlMinutes()).thenReturn(60L);
        orchestrator.agentConfig = agentConfig;

        @SuppressWarnings("unchecked")
        Instance<AgentDeliveryContext> deliveryContextInstance = Mockito.mock(Instance.class);
        when(deliveryContextInstance.isUnsatisfied()).thenReturn(true);
        orchestrator.deliveryContextInstance = deliveryContextInstance;

        @SuppressWarnings("unchecked")
        Instance<AgentRunner> agentRunnerInstance = Mockito.mock(Instance.class);
        when(agentRunnerInstance.isUnsatisfied()).thenReturn(true);
        orchestrator.agentRunnerInstance = agentRunnerInstance;

        when(notificationService.send(any(), any(), any(), any())).thenReturn(Uni.createFrom().voidItem());
        when(voicePreference.isRequested()).thenReturn(false);
        when(conversationControl.isClearRequested()).thenReturn(false);
        when(authService.isAuthorized(any())).thenReturn(true);
    }

    @Test
    void acceptedTextMessageLogsKindAndLengthWithChannelAndConversationAndNoText() {
        when(agentService.chatSync(any(), any())).thenReturn("ok");

        try (MessagingLogCapture logs = new MessagingLogCapture()) {
            orchestrator.process(MessagingMessage.text("telegram", "chat-in-1", "user-7", "secret question"))
                    .await().indefinitely();

            List<MessagingLogCapture.Line> accepted = logs.containing("Message accepted");
            assertEquals(1, accepted.size(), () -> "expected one accepted line, got " + logs.lines());
            String line = accepted.get(0).message();
            assertTrue(line.contains("provider=telegram"), line);
            assertTrue(line.contains("chatId=chat-in-1"), line);
            assertTrue(line.contains("userId=user-7"), line);
            assertTrue(line.contains("kind=text"), line);
            assertTrue(line.contains("length=15"), line);
            assertEquals("telegram", accepted.get(0).mdc().get("channel"));
            assertEquals(ConversationId.SHARED, accepted.get(0).mdc().get("conversation"));
            assertTrue(logs.lines().stream().noneMatch(l -> l.message().contains("secret question")),
                    () -> "message text reached a log line: " + logs.lines());
        }
    }

    @Test
    void rejectedSenderWarnsOnceWithProviderAndUserId() {
        when(authService.isAuthorized(any())).thenReturn(false);

        try (MessagingLogCapture logs = new MessagingLogCapture()) {
            orchestrator.process(MessagingMessage.text("discord", "chat-in-2", "intruder-9", "secret hello"))
                    .await().indefinitely();

            List<MessagingLogCapture.Line> rejected = logs.containing("Message rejected");
            assertEquals(1, rejected.size(), () -> "expected one rejection warning, got " + logs.lines());
            assertEquals(java.util.logging.Level.WARNING, rejected.get(0).level());
            assertTrue(rejected.get(0).message().contains("provider=discord"), rejected.get(0).message());
            assertTrue(rejected.get(0).message().contains("userId=intruder-9"), rejected.get(0).message());
            assertTrue(logs.containing("Message accepted").isEmpty());
            assertTrue(logs.lines().stream().noneMatch(l -> l.message().contains("secret hello")));
        }
    }

    @Test
    void transcribedVoiceLogsEngineBytesAndOutcomeButNotTheTranscript() {
        when(transcriptionService.transcribe(any())).thenReturn("secret transcript");
        when(transcriptionService.engine()).thenReturn("whisper");
        when(agentService.chatSync(any(), any())).thenReturn("ok");

        try (MessagingLogCapture logs = new MessagingLogCapture()) {
            orchestrator.process(MessagingMessage.audio("telegram", "chat-in-3", "user-3", new byte[] {1, 2, 3}))
                    .await().indefinitely();

            List<MessagingLogCapture.Line> transcribed = logs.containing("Voice transcribed");
            assertEquals(1, transcribed.size(), () -> "expected one transcription line, got " + logs.lines());
            String line = transcribed.get(0).message();
            assertTrue(line.contains("engine=whisper"), line);
            assertTrue(line.contains("audioBytes=3"), line);
            assertTrue(line.contains("outcome=ok"), line);
            assertTrue(line.matches(".*elapsedMs=\\d+.*"), line);
            assertEquals("telegram", transcribed.get(0).mdc().get("channel"));
            assertTrue(logs.lines().stream().noneMatch(l -> l.message().contains("secret transcript")),
                    () -> "transcript reached a log line: " + logs.lines());
            assertTrue(logs.containing("Message accepted").get(0).message().contains("kind=voice"));
        }
    }
}
