package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.agent.AgentService;
import dev.omatheusmesmo.qlawkus.messaging.auth.MessagingAuthService;
import io.smallrye.mutiny.Uni;
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

        orchestrator.agentService = agentService;
        orchestrator.authService = authService;
        orchestrator.notificationService = notificationService;

        when(notificationService.send(any(), any(), any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void process_authorizedUser_invokesAgentAndSendsResponse() {
        when(authService.isAuthorized(any())).thenReturn(true);
        when(agentService.chatSync("hello")).thenReturn("Hi there!");

        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "user-1", "hello");
        orchestrator.process(msg).await().indefinitely();

        verify(agentService).chatSync("hello");
        verify(notificationService).send("telegram", "chat-1", "Hi there!");
    }

    @Test
    void process_unauthorizedUser_doesNotInvokeAgent() {
        when(authService.isAuthorized(any())).thenReturn(false);

        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "intruder", "hello");
        orchestrator.process(msg).await().indefinitely();

        verify(agentService, never()).chatSync(any());
        verify(notificationService, never()).send(any(), any(), any());
    }

    @Test
    void process_agentThrows_recoversSendsErrorMessage() {
        when(authService.isAuthorized(any())).thenReturn(true);
        when(agentService.chatSync(any())).thenThrow(new RuntimeException("model unavailable"));

        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "user-1", "hello");
        assertDoesNotThrow(() -> orchestrator.process(msg).await().indefinitely());

        verify(notificationService).send(eq("telegram"), eq("chat-1"), contains("sorry"));
    }

    @Test
    void process_returnsUniThatCompletesWithoutValue() {
        when(authService.isAuthorized(any())).thenReturn(true);
        when(agentService.chatSync(any())).thenReturn("ok");

        MessagingMessage msg = MessagingMessage.text("telegram", "chat-1", "user-1", "ping");
        Void result = orchestrator.process(msg).await().indefinitely();

        assertNull(result);
    }
}
