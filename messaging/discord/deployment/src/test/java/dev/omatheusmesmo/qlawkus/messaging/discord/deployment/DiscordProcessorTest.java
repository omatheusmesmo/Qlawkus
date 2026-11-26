package dev.omatheusmesmo.qlawkus.messaging.discord.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import discord4j.gateway.json.GatewayPayload;
import discord4j.gateway.json.jackson.PayloadDeserializer;
import discord4j.voice.json.VoiceGatewayPayload;
import discord4j.voice.json.VoiceGatewayPayloadDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

/**
 * Pins what the native image needs from Caffeine for Discord4J's local store: the gateway login
 * builds its caches through generated classes that read their own static fields reflectively, so a
 * constructor-only registration let the gateway die with a missing reflection error in the native
 * image while the JVM image connected. The gateway then could not decode its first frame either:
 * Jackson builds the payload deserializers named in {@code @JsonDeserialize(using = ...)} through
 * their constructors, and the payload types themselves are read and written through their members.
 */
class DiscordProcessorTest {

    @Test
    void caffeineGeneratedCachesAreRegisteredWithTheirFields() {
        CombinedIndexBuildItem index = mock(CombinedIndexBuildItem.class);
        when(index.getIndex()).thenReturn(new Indexer().complete());
        List<ReflectiveClassBuildItem> registered = new ArrayList<>();
        BuildProducer<ReflectiveClassBuildItem> producer = registered::add;

        new DiscordProcessor().registerDiscordPayloads(index, producer);

        for (String name : List.of("com.github.benmanes.caffeine.cache.SI", "com.github.benmanes.caffeine.cache.PW")) {
            List<ReflectiveClassBuildItem> items = registered.stream()
                    .filter(item -> item.getClassNames().contains(name))
                    .toList();
            assertEquals(1, items.size(), name + " should be registered once");
            assertTrue(items.get(0).isFields(), name + " is read through its static fields");
            assertTrue(items.get(0).isConstructors(), name + " is created through its constructor");
        }
    }

    @Test
    void gatewayAndVoicePayloadsAndTheirDeserializersAreRegistered() throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> type : List.of(GatewayPayload.class, PayloadDeserializer.class,
                VoiceGatewayPayload.class, VoiceGatewayPayloadDeserializer.class)) {
            indexer.indexClass(type);
        }
        Index jandex = indexer.complete();
        CombinedIndexBuildItem index = mock(CombinedIndexBuildItem.class);
        when(index.getIndex()).thenReturn(jandex);
        List<ReflectiveClassBuildItem> registered = new ArrayList<>();
        BuildProducer<ReflectiveClassBuildItem> producer = registered::add;

        new DiscordProcessor().registerDiscordPayloads(index, producer);

        for (Class<?> type : List.of(GatewayPayload.class, PayloadDeserializer.class,
                VoiceGatewayPayload.class, VoiceGatewayPayloadDeserializer.class)) {
            List<ReflectiveClassBuildItem> items = registered.stream()
                    .filter(item -> item.getClassNames().contains(type.getName()))
                    .toList();
            assertEquals(1, items.size(), type.getName() + " should be registered once");
            assertTrue(items.get(0).isConstructors(), type.getName() + " is created by Jackson");
            assertTrue(items.get(0).isMethods(), type.getName() + " is read through its members");
            assertTrue(items.get(0).isFields(), type.getName() + " is read through its members");
        }
    }
}
