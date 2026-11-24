package dev.omatheusmesmo.qlawkus.messaging.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import discord4j.common.JacksonResources;
import discord4j.discordjson.json.GatewayData;
import discord4j.discordjson.json.gateway.GuildCreate;
import discord4j.discordjson.json.gateway.InteractionCreate;
import discord4j.discordjson.json.gateway.MessageCreate;
import discord4j.discordjson.json.gateway.Ready;
import discord4j.gateway.payload.JacksonPayloadReader;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiscordPayloadDecodingTest {

    private final ObjectMapper mapper = JacksonResources.create().getObjectMapper();
    private final JacksonPayloadReader gateway = new JacksonPayloadReader(mapper, false);

    @Test
    void readsGatewayBotResponse() throws Exception {
        try (var input = fixture("gateway-bot")) {
            var response = mapper.readValue(input, GatewayData.class);
            assertEquals("wss://gateway.discord.gg", response.url());
            assertEquals(1, response.shards().get());
            assertEquals(1, response.sessionStartLimit().get().maxConcurrency().get());
        }
    }

    @Test
    void readsReady() throws Exception {
        var ready = event("ready", Ready.class);
        assertEquals("fixture-session", ready.sessionId());
        assertTrue(ready.user().bot().get());
        assertEquals(1, ready.guilds().size());
    }

    @Test
    void readsGuildCreateWithNullVoiceChannelApplicationId() throws Exception {
        var guild = event("guild-create", GuildCreate.class).guild();
        assertEquals("Fixture server", guild.name());
        var voice = guild.channels().getFirst();
        assertEquals(2, voice.type());
        assertEquals("Fixture voice", voice.name().get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "attachment", "bot", "reply"})
    void readsMessageCreate(String variant) throws Exception {
        var message = event("message-" + variant, MessageCreate.class).message();
        assertFalse(message.content().isBlank());
        assertEquals(variant.equals("bot"), message.author().bot().toOptional().orElse(false));
        assertEquals(variant.equals("attachment") ? 1 : 0, message.attachments().size());
        assertEquals(variant.equals("reply"), !message.messageReference().isAbsent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ask", "verbose", "status", "help"})
    void readsQlawkusInteraction(String subcommand) throws Exception {
        var command = event("interaction-" + subcommand, InteractionCreate.class).interaction().data().get();
        assertEquals("qlawkus", command.name().get());
        var option = command.options().get().getFirst();
        assertEquals(subcommand, option.name());
        if (subcommand.equals("ask")) {
            assertEquals("Hello", option.options().get().getFirst().value().get());
        }
    }

    private <T> T event(String fixture, Class<T> type) throws Exception {
        try (var input = fixture(fixture)) {
            var payload = gateway.read(Unpooled.wrappedBuffer(input.readAllBytes())).block(Duration.ofSeconds(2));
            return assertInstanceOf(type, payload == null ? null : payload.getData());
        }
    }

    private InputStream fixture(String name) {
        return getClass().getResourceAsStream("/discord/" + name + ".json");
    }
}
