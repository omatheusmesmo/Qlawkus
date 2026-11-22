package dev.omatheusmesmo.qlawkus.messaging;

import dev.omatheusmesmo.qlawkus.agent.WorkflowResultReadyEvent;
import dev.omatheusmesmo.qlawkus.messaging.format.FormatterRegistry;
import dev.omatheusmesmo.qlawkus.messaging.format.MessageFormatter;
import dev.omatheusmesmo.qlawkus.task.ScheduledTaskFailedEvent;
import dev.omatheusmesmo.qlawkus.task.TaskAudit;
import dev.omatheusmesmo.qlawkus.task.TaskAuditEvent;
import dev.omatheusmesmo.qlawkus.task.TaskDestination;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationService service;
    private ProviderRegistry providerRegistry;
    private FormatterRegistry formatterRegistry;
    private ChunkingService chunkingService;
    private MessagingProvider provider;

    @BeforeEach
    void setUp() {
        service = new NotificationService();
        providerRegistry = Mockito.mock(ProviderRegistry.class);
        formatterRegistry = Mockito.mock(FormatterRegistry.class);
        chunkingService = Mockito.mock(ChunkingService.class);
        provider = Mockito.mock(MessagingProvider.class);

        service.providerRegistry = providerRegistry;
        service.formatterRegistry = formatterRegistry;
        service.chunkingService = chunkingService;

        when(provider.providerId()).thenReturn("telegram");
        when(provider.supportedFormat()).thenReturn(MessagingFormat.TELEGRAM_MARKDOWN_V2);
        when(provider.send(any(), any())).thenReturn(Uni.createFrom().voidItem());
        MessageFormatter passThrough = new MessageFormatter() {
            @Override public MessagingFormat format() { return MessagingFormat.PLAIN_TEXT; }
            @Override public String format(String text) { return text == null ? "" : text; }
        };
        when(formatterRegistry.forFormat(any())).thenReturn(passThrough);
    }

    @Test
    void send_knownProvider_delegatesToProviderSend() {
        when(providerRegistry.getProvider("telegram")).thenReturn(Optional.of(provider));
        when(chunkingService.chunk(any(), eq("telegram"))).thenReturn(List.of("hello"));

        service.send("telegram", "chat-1", "hello").await().indefinitely();

        verify(provider).send("chat-1", "hello");
    }

    @Test
    void send_unknownProvider_completesWithoutError() {
        when(providerRegistry.getProvider("unknown")).thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                service.send("unknown", "chat-1", "hello").await().indefinitely());
        verify(provider, never()).send(any(), any());
    }

    @Test
    void send_longText_sendsAllChunks() {
        when(providerRegistry.getProvider("telegram")).thenReturn(Optional.of(provider));
        when(chunkingService.chunk(any(), eq("telegram"))).thenReturn(List.of("chunk1", "chunk2", "chunk3"));

        service.send("telegram", "chat-1", "long text").await().indefinitely();

        verify(provider).send("chat-1", "chunk1");
        verify(provider).send("chat-1", "chunk2");
        verify(provider).send("chat-1", "chunk3");
    }

    @Test
    void tracksPartialDeliveryAndContinuesAfterFailure() {
        when(providerRegistry.getProvider("telegram")).thenReturn(Optional.of(provider));
        when(chunkingService.chunk(any(), eq("telegram"))).thenReturn(List.of("one", "two", "three"));
        when(provider.send("chat-1", "two")).thenThrow(new IllegalStateException("test failure"));

        var report = service.sendTracked("telegram", "chat-1", "long text", null).await().indefinitely();

        assertEquals(NotificationService.DeliveryStatus.PARTIAL, report.status());
        assertEquals(2, report.acceptedParts());
        assertEquals(3, report.totalParts());
        assertEquals("IllegalStateException", report.errorType());
        verify(provider).send("chat-1", "three");
    }

    @Test
    void distinguishesMissingProviderFromTotalFailure() {
        when(providerRegistry.getProvider("unknown")).thenReturn(Optional.empty());
        when(providerRegistry.getProvider("telegram")).thenReturn(Optional.of(provider));
        when(chunkingService.chunk(any(), eq("telegram"))).thenReturn(List.of("hello"));
        when(provider.send("chat-1", "hello")).thenReturn(Uni.createFrom().failure(new IllegalStateException()));

        var missing = service.sendTracked("unknown", "chat-1", "hello", null).await().indefinitely();
        var failed = service.sendTracked("telegram", "chat-1", "hello", null).await().indefinitely();

        assertEquals(NotificationService.DeliveryStatus.NO_PROVIDER, missing.status());
        assertEquals(NotificationService.DeliveryStatus.FAILED, failed.status());
        assertEquals(0, failed.acceptedParts());
        assertEquals(1, failed.totalParts());
    }

    @Test
    void deliveryListenerRecordsProviderRejectionUnderTheOriginalRun() {
        when(providerRegistry.getProvider("telegram")).thenReturn(Optional.of(provider));
        String notice = "Scheduled task task ran but its full reply could not be delivered.";
        when(chunkingService.chunk("hello", "telegram")).thenReturn(List.of("one", "two"));
        when(chunkingService.chunk(notice, "telegram")).thenReturn(List.of(notice));
        when(provider.send("chat-1", "two")).thenReturn(Uni.createFrom().failure(new WebApplicationException(429)));
        service.redactor = mock(SecretRedactor.class);
        var audit = mock(TaskAudit.class);
        var listener = new WorkflowResultDeliveryListener();
        listener.notificationService = service;
        listener.audit = audit;
        UUID run = UUID.randomUUID();

        listener.onWorkflowResultReady(new WorkflowResultReadyEvent("telegram", "chat-1", "hello", "task", run));

        verify(audit).record(new TaskAuditEvent("task", run, "telegram", TaskAuditEvent.Stage.DELIVERY_PARTIAL,
                null, "WebApplicationException", 429, 1, 2));
        verify(provider).send("chat-1", notice);
        verify(audit).record(new TaskAuditEvent("task", run, "telegram", TaskAuditEvent.Stage.FAILURE_NOTICE_SENT,
                null, null, null, 1, 1));
    }

    @Test
    void failedNoticeDoesNotRetryOrExposeTheExecutionError() {
        String notice = "Scheduled task task failed while running.";
        when(providerRegistry.getProvider("telegram")).thenReturn(Optional.of(provider));
        when(chunkingService.chunk(notice, "telegram")).thenReturn(List.of(notice));
        when(provider.send("chat-1", notice)).thenReturn(Uni.createFrom().failure(new IllegalStateException("test")));
        var listener = new WorkflowResultDeliveryListener();
        listener.notificationService = service;
        listener.audit = mock(TaskAudit.class);
        UUID run = UUID.randomUUID();

        listener.onScheduledTaskFailed(new ScheduledTaskFailedEvent("task", run, new TaskDestination("telegram", "chat-1")));

        verify(provider).send("chat-1", notice);
        verify(listener.audit).record(new TaskAuditEvent("task", run, "telegram", TaskAuditEvent.Stage.FAILURE_NOTICE_FAILED,
                null, "IllegalStateException", null, 0, 1));
    }

    @Test
    void broadcast_sendsToAllActiveProviders() {
        MessagingProvider discord = Mockito.mock(MessagingProvider.class);
        when(discord.providerId()).thenReturn("discord");
        when(discord.supportedFormat()).thenReturn(MessagingFormat.DISCORD_MARKDOWN);
        when(discord.send(any(), any())).thenReturn(Uni.createFrom().voidItem());

        when(providerRegistry.activeProviders()).thenReturn(List.of(provider, discord));
        when(providerRegistry.getProvider("telegram")).thenReturn(Optional.of(provider));
        when(providerRegistry.getProvider("discord")).thenReturn(Optional.of(discord));
        when(chunkingService.chunk(any(), any())).thenReturn(List.of("hi"));

        service.broadcast("hi").await().indefinitely();

        verify(provider).send(null, "hi");
        verify(discord).send(null, "hi");
    }
}
