package dev.omatheusmesmo.qlawkus.messaging.discord;

import dev.omatheusmesmo.qlawkus.messaging.MediaDownloader;
import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import dev.omatheusmesmo.qlawkus.messaging.MessagingProvider;
import dev.omatheusmesmo.qlawkus.messaging.MessagingResponse;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.util.Optional;

@ApplicationScoped
public class DiscordProviderAdapter implements MessagingProvider {

    @Inject
    DiscordConfig config;

    @Inject
    MediaDownloader mediaDownloader;

    @Inject
    MessagingOrchestrator orchestrator;

    @Inject
    DiscordSlashCommandRegistrar slashCommandRegistrar;

    volatile GatewayDiscordClient gatewayClient;

    void onStart(@Observes StartupEvent event) {
        if (config.botToken().isEmpty()) {
            Log.info("Discord: bot-token not configured, skipping Gateway connection");
            return;
        }

        IntentSet intents = IntentSet.of(
                Intent.GUILDS,
                Intent.GUILD_MESSAGES,
                Intent.MESSAGE_CONTENT,
                Intent.DIRECT_MESSAGES);

        DiscordClient.create(config.botToken().get())
                .gateway()
                .setEnabledIntents(intents)
                .login()
                .subscribe(
                        gateway -> {
                            gatewayClient = gateway;
                            setupListeners(gateway);
                            slashCommandRegistrar.register(gateway, config.applicationId().orElse(""), config.guildId());
                            postStartupGreeting(gateway);
                            Log.info("Discord: Gateway connected");
                        },
                        err -> Log.errorf(err, "Discord: Gateway login failed"));
    }

    private void postStartupGreeting(GatewayDiscordClient gateway) {
        config.startupChannelId().ifPresent(channelId ->
                gateway.getChannelById(Snowflake.of(channelId))
                        .ofType(MessageChannel.class)
                        .flatMap(channel -> channel.createMessage(config.startupGreeting()))
                        .subscribe(
                                m -> Log.infof("Discord: startup greeting posted to channel=%s", channelId),
                                err -> Log.errorf(err, "Discord: failed to post startup greeting channel=%s", channelId)));
    }

    void onStop(@Observes ShutdownEvent event) {
        if (gatewayClient != null) {
            gatewayClient.logout().subscribe(
                    v -> Log.info("Discord: Gateway logged out"),
                    err -> Log.errorf(err, "Discord: logout failed"));
        }
    }

    private void setupListeners(GatewayDiscordClient gateway) {
        gateway.on(MessageCreateEvent.class)
                .filter(this::isUserMessage)
                .filter(ev -> config.respondToAllMessages())
                .subscribe(this::handleMessage,
                        err -> Log.errorf(err, "Discord: message listener error"));

        gateway.on(ChatInputInteractionEvent.class)
                .subscribe(this::handleSlashCommand,
                        err -> Log.errorf(err, "Discord: slash command listener error"));
    }

    private boolean isUserMessage(MessageCreateEvent event) {
        return event.getMessage().getAuthor()
                .map(author -> !author.isBot())
                .orElse(false);
    }

    private void handleMessage(MessageCreateEvent event) {
        Message msg = event.getMessage();
        MessagingMessage mapped = mapMessage(msg);
        if (mapped.text().isBlank() && mapped.audio().isEmpty()) {
            if (hasAudioAttachment(msg)) {
                send(msg.getChannelId().asString(),
                        "⚠️ Não consegui processar seu áudio agora (falha ao baixar do Discord). Pode tentar enviar de novo?")
                        .subscribe().with(
                                ignored -> {},
                                err -> Log.errorf(err, "Discord: failed to notify audio download error"));
            }
            return;
        }

        var processingStage = orchestrator.process(mapped)
                .onFailure().invoke(err -> Log.errorf(err,
                        "Discord: orchestrator failed userId=%s", mapped.userId()))
                .onFailure().recoverWithNull()
                .subscribeAsCompletionStage();

        event.getMessage().getChannel()
                .ofType(MessageChannel.class)
                .flatMapMany(channel -> channel.typeUntil(Mono.fromCompletionStage(processingStage)))
                .subscribe(
                        __ -> {},
                        err -> Log.errorf(err, "Discord: typing indicator failed userId=%s", mapped.userId()));
    }

    private void handleSlashCommand(ChatInputInteractionEvent event) {
        if (!"qlawkus".equals(event.getCommandName())) {
            return;
        }

        String sub = event.getOptions().stream()
                .findFirst()
                .map(ApplicationCommandInteractionOption::getName)
                .orElse("help");

        switch (sub) {
            case "ask" -> handleAskCommand(event);
            case "status" -> event.reply("✅ Qlawkus online").subscribe();
            case "help" -> event.reply(helpText()).subscribe();
            default -> event.reply("Unknown subcommand. Use `/qlawkus help`.").subscribe();
        }
    }

    private void handleAskCommand(ChatInputInteractionEvent event) {
        String question = event.getOption("ask")
                .flatMap(opt -> opt.getOption("question"))
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("");

        if (question.isBlank()) {
            event.reply("Pergunta vazia. Use `/qlawkus ask question:<sua pergunta>`.").subscribe();
            return;
        }

        String userId = event.getInteraction().getUser().getId().asString();
        String channelId = event.getInteraction().getChannelId().asString();

        event.deferReply().subscribe();

        MessagingMessage mapped = new MessagingMessage("discord", channelId, userId, question, Optional.empty());
        orchestrator.process(mapped)
                .subscribe().with(
                        r -> {},
                        err -> Log.errorf(err, "Discord: ask command failed userId=%s", userId));
    }

    private String helpText() {
        return """
                **Qlawkus** - autonomous engineering agent

                Comandos disponíveis:
                - `/qlawkus ask question:<texto>` - faz uma pergunta ao agente
                - `/qlawkus status` - mostra status do agente
                - `/qlawkus help` - mostra esta ajuda

                Ou simplesmente envie uma mensagem no canal (se respondToAllMessages estiver ativo) ou em DM.
                """;
    }

    public MessagingMessage mapMessage(Message msg) {
        String userId = msg.getAuthor().map(User::getId).map(Snowflake::asString).orElse("unknown");
        String channelId = msg.getChannelId().asString();
        String text = msg.getContent();
        Optional<byte[]> audio = extractAudioAttachment(msg);
        return new MessagingMessage("discord", channelId, userId, text, audio);
    }

    private Optional<byte[]> extractAudioAttachment(Message msg) {
        return msg.getAttachments().stream()
                .filter(this::isAudio)
                .findFirst()
                .flatMap(this::downloadAttachment);
    }

    private boolean hasAudioAttachment(Message msg) {
        return msg.getAttachments().stream().anyMatch(this::isAudio);
    }

    private boolean isAudio(Attachment att) {
        return att.getContentType().map(ct -> ct.startsWith("audio/")).orElse(false);
    }

    private Optional<byte[]> downloadAttachment(Attachment att) {
        try {
            byte[] data = mediaDownloader.download(att.getUrl());
            Log.infof("Discord: downloaded audio attachment %s (%d bytes, %s)",
                    att.getFilename(), data.length, att.getContentType().orElse("unknown"));
            return Optional.of(data);
        } catch (Exception e) {
            Log.errorf("Discord: failed to download attachment %s after retries: %s",
                    att.getFilename(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String providerId() {
        return "discord";
    }

    @Override
    public MessagingFormat supportedFormat() {
        return MessagingFormat.DISCORD_MARKDOWN;
    }

    @Override
    public Uni<MessagingResponse> receive(MessagingMessage message) {
        return orchestrator.process(message)
                .replaceWith(new MessagingResponse(message.chatId(), ""));
    }

    @Override
    public Uni<Void> send(String channelId, String text) {
        if (gatewayClient == null) {
            Log.warnf("Discord: send called but Gateway not connected channel=%s", channelId);
            return Uni.createFrom().voidItem();
        }

        return Uni.createFrom().completionStage(
                gatewayClient.getChannelById(Snowflake.of(channelId))
                        .ofType(MessageChannel.class)
                        .flatMap(channel -> channel.createMessage(text))
                        .toFuture())
                .onFailure().invoke(err -> Log.errorf(err, "Discord: send failed channel=%s", channelId))
                .onFailure().recoverWithNull()
                .replaceWithVoid();
    }

    @Override
    public Uni<Void> sendVoice(String channelId, byte[] audio, String filename, String fallbackText) {
        if (gatewayClient == null) {
            Log.warnf("Discord: sendVoice called but Gateway not connected channel=%s", channelId);
            return Uni.createFrom().voidItem();
        }

        return Uni.createFrom().completionStage(
                gatewayClient.getChannelById(Snowflake.of(channelId))
                        .ofType(MessageChannel.class)
                        .flatMap(channel -> channel.createMessage(MessageCreateSpec.builder()
                                .addFile(filename, new ByteArrayInputStream(audio))
                                .build()))
                        .toFuture())
                .replaceWithVoid()
                .onFailure().invoke(err -> Log.errorf(err,
                        "Discord: sendVoice failed channel=%s, falling back to text", channelId))
                .onFailure().recoverWithUni(() -> send(channelId, fallbackText));
    }
}
