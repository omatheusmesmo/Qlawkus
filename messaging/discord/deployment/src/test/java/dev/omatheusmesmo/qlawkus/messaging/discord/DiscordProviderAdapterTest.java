package dev.omatheusmesmo.qlawkus.messaging.discord;

import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.spec.MessageCreateFields;
import discord4j.core.spec.MessageCreateSpec;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    void send_returnsVoidWhenGatewayNotConnected() {
        adapter.gatewayClient = null;

        adapter.send("chan-1", "hello").await().indefinitely();
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
