package dev.omatheusmesmo.qlawkus.messaging.discord;

import dev.omatheusmesmo.qlawkus.attachment.AttachmentRejected;
import dev.omatheusmesmo.qlawkus.messaging.DeliveryFailure;
import dev.omatheusmesmo.qlawkus.messaging.MediaDownloader;
import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.MessageCreateFields;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.discordjson.json.MessageCreateRequest;
import discord4j.discordjson.json.MessageData;
import discord4j.gateway.GatewayClient;
import discord4j.gateway.GatewayClientGroup;
import discord4j.rest.RestClient;
import discord4j.rest.entity.RestChannel;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.http.client.ClientRequest;
import discord4j.rest.json.response.ErrorResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.smallrye.mutiny.Uni;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClientResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;

class DiscordProviderAdapterTest {

    private DiscordProviderAdapter adapter;
    private DiscordConfig config;
    private MediaDownloader downloader;
    private MessagingOrchestrator orchestrator;
    private DiscordSlashCommandRegistrar registrar;

    @BeforeEach
    void setUp() {
        config = Mockito.mock(DiscordConfig.class);
        downloader = Mockito.mock(MediaDownloader.class);
        orchestrator = Mockito.mock(MessagingOrchestrator.class);
        registrar = Mockito.mock(DiscordSlashCommandRegistrar.class);
        adapter = new DiscordProviderAdapter(config, downloader, orchestrator, registrar);

        when(orchestrator.process(any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void providerId_isDiscord() {
        assertEquals("discord", adapter.providerId());
    }

    @Test
    void supportedFormat_isDiscordMarkdown() {
        assertEquals(MessagingFormat.DISCORD_MARKDOWN, adapter.supportedFormat());
    }

    @Test
    void failedLoginRetriesAndRegistersListenersAfterRecovery() {
        var gateway = mockGateway();
        var attempts = new AtomicInteger();
        try {
            adapter.connect(Mono.defer(() -> attempts.incrementAndGet() == 1
                    ? Mono.error(new IOException("temporary network failure"))
                    : Mono.just(gateway)));

            verify(registrar, timeout(5000)).register(gateway, "", Optional.empty());
            verify(gateway).on(MessageCreateEvent.class);
            verify(gateway).on(ChatInputInteractionEvent.class);
            assertSame(gateway, adapter.gateway());
            assertEquals(2, attempts.get());
        } finally {
            adapter.stop();
        }
    }

    @Test
    void shutdownCancelsInFlightLogin() {
        Runnable onCancel = Mockito.mock(Runnable.class);
        adapter.connect(Mono.<GatewayDiscordClient>never().doOnCancel(onCancel));

        assertNull(adapter.stop());
        verify(onCancel).run();
    }

    @Test
    void readinessReportsGatewayStateWithoutTakingHttpReadinessDown() {
        var check = new DiscordReadinessCheck(config, adapter);
        assertReadiness(check, "disabled");

        when(config.botToken()).thenReturn(Optional.of("fixture-token"));
        assertReadiness(check, "degraded");

        var gateway = mockGateway();
        var group = Mockito.mock(GatewayClientGroup.class);
        var shard = Mockito.mock(GatewayClient.class);
        when(gateway.getGatewayClientGroup()).thenReturn(group);
        when(group.getShardCount()).thenReturn(1);
        when(gateway.getGatewayClient(0)).thenReturn(Optional.of(shard));
        when(shard.isConnected()).thenReturn(Mono.just(true), Mono.just(false));
        adapter.connect(Mono.just(gateway));

        assertReadiness(check, "connected");
        assertReadiness(check, "degraded");
        assertSame(gateway, adapter.stop());
        assertReadiness(check, "degraded");
    }

    private void assertReadiness(DiscordReadinessCheck check, String status) {
        var response = check.call().await().atMost(Duration.ofSeconds(2));
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals(status, response.getData().orElseThrow().get("status"));
    }

    private GatewayDiscordClient mockGateway() {
        var gateway = Mockito.mock(GatewayDiscordClient.class);
        when(gateway.on(MessageCreateEvent.class)).thenReturn(Flux.empty());
        when(gateway.on(ChatInputInteractionEvent.class)).thenReturn(Flux.empty());
        return gateway;
    }

    @Test
    void receive_delegatesToOrchestrator() {
        MessagingMessage msg = MessagingMessage.text("discord", "chan-1", "user-1", "hello");

        adapter.receive(msg).await().indefinitely();

        verify(orchestrator).process(msg);
    }

    @Test
    void mapsAttachmentsWithoutDownloadingAndRejectsUnknownHosts() {
        var message = Mockito.mock(Message.class);
        var platform = Mockito.mock(Attachment.class);
        var unknown = Mockito.mock(Attachment.class);
        when(message.getContent()).thenReturn("What is this?");
        when(message.getChannelId()).thenReturn(Snowflake.of(42L));
        when(message.getAttachments()).thenReturn(List.of(platform, unknown));
        when(platform.getSize()).thenReturn(3);
        when(platform.getContentType()).thenReturn(Optional.of("image/png"));
        when(platform.getUrl()).thenReturn("https://cdn.discordapp.com/attachments/channel/item/pixel.png");
        when(unknown.getSize()).thenReturn(3);
        when(unknown.getContentType()).thenReturn(Optional.of("image/jpeg"));
        when(unknown.getUrl()).thenReturn("https://cdn.discordapp.com.example.org/attachments/image.jpg");
        var safeUri = URI.create("https://cdn.discordapp.com/attachments/channel/item/pixel.png");
        when(downloader.downloadAttachment(safeUri)).thenReturn(new byte[]{1, 2, 3});

        var ownerMessage = adapter.mapMessage(message);

        assertEquals("What is this?", ownerMessage.text());
        assertEquals(2, ownerMessage.attachments().size());
        verifyNoInteractions(downloader);
        assertThrows(AttachmentRejected.class, ownerMessage.attachments().getLast()::read);
        verifyNoInteractions(downloader);
        assertArrayEquals(new byte[]{1, 2, 3}, ownerMessage.attachments().getFirst().read());
        verify(downloader).downloadAttachment(safeUri);
    }

    @Test
    void send_gatewayNotConnected_propagatesToTheCaller() {
        assertThrows(IllegalStateException.class, () -> adapter.send("chan-1", "hello").await().indefinitely());
    }

    @Test
    void send_reusesAnEnforcedNonceWhenAConnectionDrops() {
        RestChannel channel = mockRestChannel();
        when(channel.createMessage(any(MessageCreateRequest.class)))
                .thenReturn(Mono.error(new IOException("connection closed")),
                        Mono.just(Mockito.mock(MessageData.class)));

        adapter.send("123", "hello").await().atMost(Duration.ofSeconds(3));

        var request = ArgumentCaptor.forClass(MessageCreateRequest.class);
        verify(channel, Mockito.times(2)).createMessage(request.capture());
        var attempts = request.getAllValues();
        assertEquals(attempts.getFirst().nonce().get(), attempts.getLast().nonce().get());
        assertTrue(attempts.getFirst().enforceNonce().get());
        assertTrue(attempts.getLast().enforceNonce().get());
        assertEquals("hello", attempts.getLast().content().get());
    }

    @Test
    void send_treatsAnEmptyAcknowledgementAsAFailure() {
        RestChannel channel = mockRestChannel();
        when(channel.createMessage(any(MessageCreateRequest.class))).thenReturn(Mono.empty());

        assertThrows(IllegalStateException.class, () -> adapter.send("123", "hello").await().indefinitely());
        verify(channel).createMessage(any(MessageCreateRequest.class));
    }

    private RestChannel mockRestChannel() {
        var gateway = mockGateway();
        RestClient rest = Mockito.mock(RestClient.class);
        RestChannel channel = Mockito.mock(RestChannel.class);
        when(gateway.getRestClient()).thenReturn(rest);
        when(rest.getChannelById(Snowflake.of("123"))).thenReturn(channel);
        adapter.connect(Mono.just(gateway));
        return channel;
    }

    @Test
    void sendsAFileToTheSuppliedChannelAndRequiresAcknowledgement() throws IOException {
        var gateway = mockGateway();
        MessageChannel channel = Mockito.mock(MessageChannel.class);
        when(gateway.getChannelById(Snowflake.of("123"))).thenReturn(Mono.just(channel));
        when(channel.createMessage(any(MessageCreateSpec.class)))
                .thenReturn(Mono.just(Mockito.mock(Message.class)), Mono.empty());
        byte[] report = "item,count\nbooks,2\n".getBytes(StandardCharsets.UTF_8);

        adapter.connect(Mono.just(gateway));
        adapter.sendFile("123", "report.csv", "text/csv", report).await().indefinitely();
        var request = ArgumentCaptor.forClass(MessageCreateSpec.class);
        verify(channel).createMessage(request.capture());
        var attachment = request.getValue().files().getFirst();
        assertEquals("report.csv", attachment.name());
        try (var stream = attachment.inputStream()) {
            assertArrayEquals(report, stream.readAllBytes());
        }
        assertThrows(IllegalStateException.class,
                () -> adapter.sendFile("123", "report.csv", "text/csv", report).await().indefinitely());
    }

    @Test
    void guildMessage_followsRespondToAllMessages() {
        respondToMessages(false, true);

        assertFalse(adapter.isEnabledForChannel(event(Snowflake.of(1L))));

        respondToMessages(true, true);

        assertTrue(adapter.isEnabledForChannel(event(Snowflake.of(1L))));
    }

    /** Quieting the bot in guild channels must not silence the owner's DM - they are separate knobs. */
    @Test
    void directMessage_survivesRespondToAllMessagesBeingOff() {
        respondToMessages(false, true);

        assertTrue(adapter.isEnabledForChannel(event(null)));
    }

    @Test
    void directMessage_isSilencedOnlyByItsOwnToggle() {
        respondToMessages(true, false);

        assertFalse(adapter.isEnabledForChannel(event(null)));
    }

    /**
     * The attachment name is what Discord shows and what the voice note is played back as, so it has
     * to survive the spec building. discord4j 3.3 dropped {@code addFile(String, InputStream)} in
     * favour of a three-argument overload that takes a description between the name and the stream,
     * which makes an off-by-one in the argument list compile cleanly and silently rename the file.
     */
    @Test
    void voiceMessage_attachesTheAudioUnderTheGivenFilename() throws IOException {
        byte[] audio = "ogg-bytes".getBytes(StandardCharsets.UTF_8);

        MessageCreateSpec spec = DiscordProviderAdapter.voiceMessage(audio, "reply.ogg");

        List<MessageCreateFields.File> files = spec.files();
        assertEquals(1, files.size());
        assertEquals("reply.ogg", files.get(0).name());
        try (InputStream content = files.get(0).inputStream()) {
            assertArrayEquals(audio, content.readAllBytes());
        }
    }

    @Test
    void aRejectedDiscordCallNamesTheHttpStatusAndDiscordsErrorCodeAndMessage() {
        ClientRequest request = Mockito.mock(ClientRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getUrl()).thenReturn("https://discord.com/api/channels/1/messages");
        HttpClientResponse response = Mockito.mock(HttpClientResponse.class);
        when(response.status()).thenReturn(HttpResponseStatus.FORBIDDEN);
        ErrorResponse body = new ErrorResponse();
        body.anySetter("code", 50013);
        body.anySetter("message", "Missing Permissions");
        Throwable failure = new IllegalStateException(new ClientException(request, response, body));

        Optional<DeliveryFailure> detail = adapter.deliveryFailure(failure);

        assertEquals("httpStatus=403 providerCode=50013 providerMessage=Missing Permissions",
                detail.orElseThrow().describe(text -> text));
    }

    @Test
    void aFailureOutsideDiscordHasNoDiscordDetail() {
        assertTrue(adapter.deliveryFailure(new IllegalStateException("Discord Gateway not connected")).isEmpty());
    }

    private void respondToMessages(boolean respondToAll, boolean respondToDirect) {
        when(config.respondToAllMessages()).thenReturn(respondToAll);
        when(config.respondToDirectMessages()).thenReturn(respondToDirect);
    }

    /** A {@code null} guild id is how Discord models a DM: no guild, just the two participants. */
    private MessageCreateEvent event(Snowflake guildId) {
        MessageCreateEvent event = Mockito.mock(MessageCreateEvent.class);
        when(event.getGuildId()).thenReturn(Optional.ofNullable(guildId));
        return event;
    }
}
