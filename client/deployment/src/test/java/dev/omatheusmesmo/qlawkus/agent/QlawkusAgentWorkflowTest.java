package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.output.OutputParsingException;
import dev.omatheusmesmo.qlawkus.cognition.GoogleAuthRequiredIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QlawkusAgentWorkflowTest {

    private QlawkusAgentWorkflow workflow;
    private UntypedAgent mockAgent;
    private AgenticScope mockScope;
    private GoogleAuthRequiredIndicator authIndicator;

    @BeforeEach
    void setUp() {
        workflow = new QlawkusAgentWorkflow();
        authIndicator = new GoogleAuthRequiredIndicator();
        workflow.authRequiredIndicator = authIndicator;

        mockAgent = Mockito.mock(UntypedAgent.class);
        mockScope = Mockito.mock(AgenticScope.class);
        workflow.workflow = mockAgent;
    }

    @Test
    void invoke_returnsResultFromWorkflow() {
        ResultWithAgenticScope<String> result = new ResultWithAgenticScope<>(mockScope, "Hello from agent");
        when(mockAgent.invokeWithAgenticScope(any())).thenReturn(result);

        String response = workflow.invoke("mem-1", "hi");

        assertEquals("Hello from agent", response);
    }

    @Test
    void invoke_returnsNullWhenResultIsNull() {
        when(mockAgent.invokeWithAgenticScope(any())).thenReturn(null);

        String response = workflow.invoke("mem-1", "hi");

        assertNull(response);
    }

    @Test
    void invoke_catchesAgentInvocationException_withOutputParsingCause_andExtractsText() {
        OutputParsingException ope = new OutputParsingException(
            "Failed to parse \"Hey you! What's up?\" (base64: \"SGV5IHlvdSE=\") into java.lang.Object",
            new RuntimeException("cause"));
        AgentInvocationException aie = new AgentInvocationException(ope);
        when(mockAgent.invokeWithAgenticScope(any())).thenThrow(aie);

        String response = workflow.invoke("mem-1", "hi");

        assertEquals("Hey you! What's up?", response);
    }

    @Test
    void invoke_catchesAgentInvocationException_withOutputParsingCause_fallbackToIntMarker() {
        OutputParsingException ope = new OutputParsingException(
            "Failed to parse \"Simple reply\" into java.lang.Object",
            new RuntimeException("cause"));
        AgentInvocationException aie = new AgentInvocationException(ope);
        when(mockAgent.invokeWithAgenticScope(any())).thenThrow(aie);

        String response = workflow.invoke("mem-1", "hi");

        assertEquals("Simple reply", response);
    }

    @Test
    void invoke_catchesAgentInvocationException_withJsonBlock() {
        OutputParsingException ope = new OutputParsingException(
            "Failed to parse \"```json\n{\"reply\": \"hello\"}\n```\" (base64: \"YGBg\") into java.lang.Object",
            new RuntimeException("cause"));
        AgentInvocationException aie = new AgentInvocationException(ope);
        when(mockAgent.invokeWithAgenticScope(any())).thenThrow(aie);

        String response = workflow.invoke("mem-1", "hi");

        assertEquals("{\"reply\": \"hello\"}", response);
    }

    @Test
    void invoke_catchesAgentInvocationException_withNullMessage() {
        OutputParsingException ope = new OutputParsingException(null, new RuntimeException("cause"));
        AgentInvocationException aie = new AgentInvocationException(ope);
        when(mockAgent.invokeWithAgenticScope(any())).thenThrow(aie);

        String response = workflow.invoke("mem-1", "hi");

        assertNull(response);
    }

    @Test
    void invoke_propagatesAgentInvocationException_withoutOutputParsingCause() {
        AgentInvocationException aie = new AgentInvocationException("some other error");
        when(mockAgent.invokeWithAgenticScope(any())).thenThrow(aie);

        assertThrows(AgentInvocationException.class, () -> workflow.invoke("mem-1", "hi"));
    }

    @Test
    void invoke_propagatesNonAgentInvocationException() {
        when(mockAgent.invokeWithAgenticScope(any())).thenThrow(new RuntimeException("model down"));

        assertThrows(RuntimeException.class, () -> workflow.invoke("mem-1", "hi"));
    }

    @Test
    void completeOAuthApproval_noScope_returnsFalse() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(null);

        assertFalse(workflow.completeOAuthApproval("mem-1", "approved"));
    }

    @Test
    void completeOAuthApproval_scopeCompletes_returnsTrue() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(mockScope);
        when(mockScope.completePendingResponse("google-oauth-approval", "approved")).thenReturn(true);

        assertTrue(workflow.completeOAuthApproval("mem-1", "approved"));
    }

    @Test
    void completeOAuthApproval_scopeFails_returnsFalse() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(mockScope);
        when(mockScope.completePendingResponse("google-oauth-approval", "approved")).thenReturn(false);

        assertFalse(workflow.completeOAuthApproval("mem-1", "approved"));
    }

    @Test
    void hasPendingOAuth_noScope_returnsFalse() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(null);

        assertFalse(workflow.hasPendingOAuth("mem-1"));
    }

    @Test
    void hasPendingOAuth_withPending_returnsTrue() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(mockScope);
        when(mockScope.pendingResponseIds()).thenReturn(Set.of("google-oauth-approval"));

        assertTrue(workflow.hasPendingOAuth("mem-1"));
    }

    @Test
    void hasPendingOAuth_noPending_returnsFalse() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(mockScope);
        when(mockScope.pendingResponseIds()).thenReturn(Set.of());

        assertFalse(workflow.hasPendingOAuth("mem-1"));
    }

    @Test
    void storeDeliveryInfo_writesProviderAndChatToScope() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(mockScope);

        workflow.storeDeliveryInfo("mem-1", "telegram", "chat-1");

        verify(mockScope).writeState("deliveryProviderId", "telegram");
        verify(mockScope).writeState("deliveryChatId", "chat-1");
    }

    @Test
    void storeDeliveryInfo_noScope_doesNothing() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(null);

        workflow.storeDeliveryInfo("mem-1", "telegram", "chat-1");

        verify(mockScope, never()).writeState(anyString(), any());
    }

    @Test
    void getDeliveryInfo_returnsInfoWhenPresent() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(mockScope);
        when(mockScope.readState("deliveryProviderId", null)).thenReturn("telegram");
        when(mockScope.readState("deliveryChatId", null)).thenReturn("chat-1");

        QlawkusAgentWorkflow.DeliveryInfo info = workflow.getDeliveryInfo("mem-1");

        assertNotNull(info);
        assertEquals("telegram", info.providerId());
        assertEquals("chat-1", info.chatId());
    }

    @Test
    void getDeliveryInfo_noScope_returnsNull() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(null);

        assertNull(workflow.getDeliveryInfo("mem-1"));
    }

    @Test
    void getDeliveryInfo_missingProvider_returnsNull() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(mockScope);
        when(mockScope.readState("deliveryProviderId", null)).thenReturn(null);
        when(mockScope.readState("deliveryChatId", null)).thenReturn("chat-1");

        assertNull(workflow.getDeliveryInfo("mem-1"));
    }

    @Test
    void getDeliveryInfo_missingChat_returnsNull() {
        when(mockAgent.getAgenticScope("mem-1")).thenReturn(mockScope);
        when(mockScope.readState("deliveryProviderId", null)).thenReturn("telegram");
        when(mockScope.readState("deliveryChatId", null)).thenReturn(null);

        assertNull(workflow.getDeliveryInfo("mem-1"));
    }

    @Test
    void evictScope_delegatesToAgenticScopeAccess() {
        when(mockAgent.evictAgenticScope("mem-1")).thenReturn(true);

        workflow.evictScope("mem-1");

        verify(mockAgent).evictAgenticScope("mem-1");
    }

    @Test
    void invoke_passesCorrectInputMap() {
        ResultWithAgenticScope<String> result = new ResultWithAgenticScope<>(mockScope, "ok");
        when(mockAgent.invokeWithAgenticScope(any())).thenReturn(result);

        workflow.invoke("mem-42", "hello world");

        verify(mockAgent).invokeWithAgenticScope(Map.of(
                "input", Map.of("message", "hello world"),
                "memoryId", "mem-42"
        ));
    }
}
