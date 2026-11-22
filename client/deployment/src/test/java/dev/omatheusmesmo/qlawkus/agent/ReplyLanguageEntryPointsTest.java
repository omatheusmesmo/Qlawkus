package dev.omatheusmesmo.qlawkus.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.omatheusmesmo.qlawkus.cognition.SoulEngine;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.event.Event;
import jakarta.interceptor.InvocationContext;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReplyLanguageEntryPointsTest {
    @Test
    void declarativeMessagingRunnerBindsTheSelectedLanguageWithoutChangingTheUserMessage() {
        AgentRunner runner = new AgentRunner();
        runner.languages = new ReplyLanguageResolver((message, hint, stable, choices, fallback) -> "pt");
        runner.agent = mock(QlawkusAgent.class);
        runner.soulEngine = mock(SoulEngine.class);
        when(runner.soulEngine.getSystemMessage("messaging")).thenAnswer(ignored ->
                Optional.of(AgentTurn.current().replyStyle().systemContext()));
        when(runner.agent.chat(eq("messaging"), contains("Write the entire reply in Portuguese (pt)"), eq("como você está?")))
                .thenReturn("Bem.");
        assertEquals("Bem.", runner.chat("messaging", "como você está?"));
        verify(runner.agent).chat(eq("messaging"), contains("Write the entire reply in Portuguese (pt)"), eq("como você está?"));
    }

    @Test
    void directSynchronousAiServiceCallsUseTheSameResolver() throws Exception {
        AgentLogInterceptor interceptor = new AgentLogInterceptor();
        interceptor.languages = new ReplyLanguageResolver((message, hint, stable, choices, fallback) -> "pt");
        InvocationContext invocation = mock(InvocationContext.class);
        when(invocation.getMethod()).thenReturn(AgentService.class.getMethod("chatSync", String.class, String.class));
        when(invocation.getParameters()).thenReturn(new Object[] {"rest-sync", "olá"});
        when(invocation.proceed()).thenAnswer(ignored -> AgentTurn.current().replyStyle().language());
        assertEquals("pt", interceptor.logInvocation(invocation));
    }

    @Test
    void streamingAndAlreadyPreparedVoiceDoNotClassifyATurnTwiceEvenWhenUnknown() {
        AtomicInteger decisions = new AtomicInteger();
        var resolver = new ReplyLanguageResolver((message, hint, stable, choices, fallback) -> {
            decisions.incrementAndGet(); return null;
        });
        AgentService agent = mock(AgentService.class);
        when(agent.chatEvents(anyString(), anyString())).thenReturn(Multi.createFrom().empty());
        @SuppressWarnings("unchecked")
        Event<ToolActivityEvent> activity = mock(Event.class);
        AgentStream stream = new AgentStream(agent, activity, mock(SecretRedactor.class),
                mock(AgentDeliveryContext.class), mock(StreamedToolContext.class), resolver);
        stream.stream("rest-stream", "olá").collect().asList().await().atMost(Duration.ofSeconds(2));
        assertEquals(1, decisions.get());
        AgentReplyStyle voice = resolver.resolve("voice", "olá", AgentReplyStyle.speech(null));
        stream.streamPrepared("voice", "olá", voice).collect().asList().await().atMost(Duration.ofSeconds(2));
        assertEquals(2, decisions.get());
    }
}
