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
import dev.omatheusmesmo.qlawkus.messaging.progress.ToolProgressRouter;
import dev.omatheusmesmo.qlawkus.messaging.progress.ToolProgressSettings;
import dev.omatheusmesmo.qlawkus.messaging.tts.TtsConfig;
import dev.omatheusmesmo.qlawkus.messaging.tts.TtsRouter;
import dev.omatheusmesmo.qlawkus.store.WorkingMemoryStore;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Instance;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessagingOrchestratorTest {

    private MessagingOrchestrator orchestrator;
    private AgentService agentService;
    private MessagingAuthService authService;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        orchestrator = new MessagingOrchestrator();
        agentService = Mockito.mock(AgentService.class);
        authService = Mockito.mock(MessagingAuthService.class);
        notificationService = Mockito.mock(NotificationService.class);
        VoiceResponsePreference voicePreference = Mockito.mock(VoiceResponsePreference.class);
        ConversationControl conversationControl = Mockito.mock(ConversationControl.class);
        WorkingMemoryStore workingMemoryStore = Mockito.mock(WorkingMemoryStore.class);
        TtsRouter ttsRouter = Mockito.mock(TtsRouter.class);

        orchestrator.agentService = agentService;
        orchestrator.authService = authService;
        orchestrator.notificationService = notificationService;
        orchestrator.voicePreference = voicePreference;
        orchestrator.conversationControl = conversationControl;
        orchestrator.workingMemoryStore = workingMemoryStore;
        orchestrator.ttsRouter = ttsRouter;
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
    }

    @Test
    void process_authorizedUser_invokesAgentAndSendsResponse() {
        when(authService.isAuthorized(any())).thenReturn(true);
        when(agentService.chatSync(anyString(), eq("hello"))).thenReturn("Hi there!");

        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "user-1", "hello");
        orchestrator.process(msg).await().indefinitely();

        verify(agentService).chatSync(any(), eq("hello"));
        verify(notificationService).send("telegram", "chat-1", "Hi there!", ConversationId.SHARED);
    }

    @Test
    void process_unauthorizedUser_doesNotInvokeAgent() {
        when(authService.isAuthorized(any())).thenReturn(false);

        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "intruder", "hello");
        orchestrator.process(msg).await().indefinitely();

        verify(agentService, never()).chatSync(any(), any());
        verify(notificationService, never()).send(any(), any(), any(), any());
    }

    @Test
    void process_agentThrows_recoversSendsErrorMessage() {
        when(authService.isAuthorized(any())).thenReturn(true);
        when(agentService.chatSync(any(), any())).thenThrow(new RuntimeException("model unavailable"));

        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "user-1", "hello");
        assertDoesNotThrow(() -> orchestrator.process(msg).await().indefinitely());

        verify(notificationService).send(eq("telegram"), eq("chat-1"), contains("Failed"), any());
    }

    @Test
    void process_returnsUniThatCompletesWithoutValue() {
        when(authService.isAuthorized(any())).thenReturn(true);
        when(agentService.chatSync(any(), any())).thenReturn("ok");

        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "user-1", "ping");
        Void result = orchestrator.process(msg).await().indefinitely();

        assertNull(result);
    }

    @Test
    void anUnauthorizedUserCannotCycleTheToolProgressMode() {
        when(authService.isAuthorized(any())).thenReturn(false);
        MessagingMessage message = new MessagingMessage("discord", "channel-1", "stranger", "/verbose", Optional.empty());

        String reply = orchestrator.cycleToolProgress(message);

        verify(orchestrator.toolProgress, never()).cycle(anyString());
        assertFalse(reply.contains("Tool progress for this chat"), reply);
    }

    @Test
    void anAuthorizedUserCyclesTheModeOfTheirChat() {
        when(authService.isAuthorized(any())).thenReturn(true);
        when(orchestrator.toolProgress.cycle("discord:channel-1")).thenReturn(ToolProgress.VERBOSE);
        MessagingMessage message = new MessagingMessage("discord", "channel-1", "owner", "/verbose", Optional.empty());

        assertEquals("Tool progress for this chat: verbose", orchestrator.cycleToolProgress(message));
    }
}
