package dev.omatheusmesmo.qlawkus.messaging.discord;

import dev.omatheusmesmo.qlawkus.attachment.AttachmentRejected;
import dev.omatheusmesmo.qlawkus.messaging.DeliveryFailure;
import dev.omatheusmesmo.qlawkus.messaging.MediaDownloader;
import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateFields;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.http.client.ClientRequest;
import discord4j.rest.json.response.ErrorResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.smallrye.mutiny.Uni;
import reactor.netty.http.client.HttpClientResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DiscordProviderAdapterTest {

    private DiscordProviderAdapter adapter;
    private MessagingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        adapter = new DiscordProviderAdapter();
        orchestrator = Mockito.mock(MessagingOrchestrator.class);
        adapter.orchestrator = orchestrator;

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
    void receive_delegatesToOrchestrator() {
        MessagingMessage msg = MessagingMessage.text("discord", "chan-1", "user-1", "hello");

        adapter.receive(msg).await().indefinitely();

        verify(orchestrator).process(msg);
    }

    @Test
    void mapsAttachmentsWithoutDownloadingAndRejectsUnknownHosts() {
        var downloader = Mockito.mock(MediaDownloader.class);
        adapter.mediaDownloader = downloader;
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
        adapter.gatewayClient = null;

        assertThrows(IllegalStateException.class, () -> adapter.send("chan-1", "hello").await().indefinitely());
    }

    @Test
    void guildMessage_followsRespondToAllMessages() {
        adapter.config = config(false, true);

        assertFalse(adapter.isEnabledForChannel(event(Snowflake.of(1L))));

        adapter.config = config(true, true);

        assertTrue(adapter.isEnabledForChannel(event(Snowflake.of(1L))));
    }

    /** Quieting the bot in guild channels must not silence the owner's DM - they are separate knobs. */
    @Test
    void directMessage_survivesRespondToAllMessagesBeingOff() {
        adapter.config = config(false, true);

        assertTrue(adapter.isEnabledForChannel(event(null)));
    }

    @Test
    void directMessage_isSilencedOnlyByItsOwnToggle() {
        adapter.config = config(true, false);

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

    private DiscordConfig config(boolean respondToAll, boolean respondToDirect) {
        DiscordConfig config = Mockito.mock(DiscordConfig.class);
        when(config.respondToAllMessages()).thenReturn(respondToAll);
        when(config.respondToDirectMessages()).thenReturn(respondToDirect);
        return config;
    }

    /** A {@code null} guild id is how Discord models a DM: no guild, just the two participants. */
    private MessageCreateEvent event(Snowflake guildId) {
        MessageCreateEvent event = Mockito.mock(MessageCreateEvent.class);
        when(event.getGuildId()).thenReturn(Optional.ofNullable(guildId));
        return event;
    }
}
