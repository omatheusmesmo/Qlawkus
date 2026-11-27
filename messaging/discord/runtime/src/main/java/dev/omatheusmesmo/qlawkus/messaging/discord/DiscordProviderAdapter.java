package dev.omatheusmesmo.qlawkus.messaging.discord;

import dev.omatheusmesmo.qlawkus.logging.FailureChain;
import dev.omatheusmesmo.qlawkus.attachment.AttachmentRejected;
import dev.omatheusmesmo.qlawkus.attachment.ChatAttachment;
import dev.omatheusmesmo.qlawkus.messaging.DeliveryFailure;
import dev.omatheusmesmo.qlawkus.messaging.MediaDownloader;
import dev.omatheusmesmo.qlawkus.messaging.MessagingFormat;
import dev.omatheusmesmo.qlawkus.messaging.MessagingMessage;
import dev.omatheusmesmo.qlawkus.messaging.MessagingOrchestrator;
import dev.omatheusmesmo.qlawkus.messaging.MessagingProvider;
import dev.omatheusmesmo.qlawkus.messaging.MessagingResponse;
import dev.omatheusmesmo.qlawkus.messaging.auth.MessagingAuthService;
import dev.omatheusmesmo.qlawkus.messaging.progress.ToolProgressSink;
import discord4j.common.ReactorResources;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.MessageCreateFields;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.json.MessageCreateRequest;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.json.response.ErrorResponse;
import discord4j.rest.response.ResponseFunction;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.net.URI;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@ApplicationScoped
public class DiscordProviderAdapter implements MessagingProvider {

    /**
     * Idle time after which the HTTP pool drops a connection. Discord's edge closes idle connections
     * on its own, and a task that fires once a day found the pool full of connections already reset
     * (observed as {@code Connection reset by peer} on the first write). Evicting well before that
     * means the next request opens a fresh connection instead of reusing a dead one.
     */
    static final Duration HTTP_MAX_IDLE_TIME = Duration.ofSeconds(20);

    /** How often the pool looks for connections past {@link #HTTP_MAX_IDLE_TIME}, in the background. */
    static final Duration HTTP_EVICT_INTERVAL = Duration.ofSeconds(10);

    /**
     * Retries a send whose connection failed. Several pooled connections can be stale at once, so a
     * single retry is not enough; only I/O failures are retried, so a refusal from Discord is never
     * repeated. Every send carries one enforced nonce, which makes a retry after a lost reply safe.
     */
    private static final Retry SEND_RETRY = Retry.backoff(3, Duration.ofMillis(250))
            .filter(failure -> FailureChain.first(failure, IOException.class).isPresent())
            .onRetryExhaustedThrow((spec, signal) -> signal.failure());

    private static final Retry LOGIN_RETRY = Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofMinutes(1))
            .doBeforeRetry(signal -> Log.warnf(signal.failure(),
                    "Discord: Gateway login attempt %d failed, retrying", signal.totalRetries() + 1));

    private final DiscordConfig config;
    private final MediaDownloader mediaDownloader;
    private final MessagingOrchestrator orchestrator;
    private final DiscordSlashCommandRegistrar slashCommandRegistrar;
    private final MessagingAuthService authService;
    private volatile GatewayDiscordClient gatewayClient;
    private Disposable loginSubscription;
    private boolean stopping;

    DiscordProviderAdapter(DiscordConfig config, MediaDownloader mediaDownloader, MessagingOrchestrator orchestrator,
            DiscordSlashCommandRegistrar slashCommandRegistrar, MessagingAuthService authService) {
        this.config = config;
        this.mediaDownloader = mediaDownloader;
        this.orchestrator = orchestrator;
        this.slashCommandRegistrar = slashCommandRegistrar;
        this.authService = authService;
    }

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

        var login = DiscordClientBuilder.create(config.botToken().get())
                .setReactorResources(reactorResources())
                // Discord4j's request queue already retries 429s automatically; 5xx needs this
                // explicit opt-in.
                .onClientResponse(ResponseFunction.retryOnceOnErrorStatus(500, 502, 503, 504))
                .build()
                .gateway()
                .setEnabledIntents(intents)
                .login();
        connect(login);
    }

    static ReactorResources reactorResources() {
        ConnectionProvider pool = ConnectionProvider.builder("discord4j")
                .maxIdleTime(HTTP_MAX_IDLE_TIME)
                .evictInBackground(HTTP_EVICT_INTERVAL)
                .build();
        return ReactorResources.builder().httpClient(ReactorResources.newHttpClient(pool)).build();
    }

    synchronized void connect(Mono<GatewayDiscordClient> login) {
        if (stopping) {
            return;
        }
        loginSubscription = login.retryWhen(LOGIN_RETRY)
                .subscribe(this::gatewayConnected, err -> Log.errorf(err, "Discord: Gateway connection handler failed"));
    }

    private synchronized void gatewayConnected(GatewayDiscordClient gateway) {
        if (stopping) {
            gateway.logout().subscribe(ignored -> {},
                    error -> Log.warnf(error, "Discord: late Gateway logout failed"));
            return;
        }
        gatewayClient = gateway;
        setupListeners(gateway);
        slashCommandRegistrar.register(gateway, config.applicationId().orElse(""), config.guildId());
        postStartupGreeting(gateway);
        Log.info("Discord: Gateway connected");
    }

    synchronized GatewayDiscordClient stop() {
        stopping = true;
        if (loginSubscription != null) {
            loginSubscription.dispose();
        }
        GatewayDiscordClient gateway = gatewayClient;
        gatewayClient = null;
        return gateway;
    }

    GatewayDiscordClient gateway() {
        return gatewayClient;
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

    private void setupListeners(GatewayDiscordClient gateway) {
        gateway.on(MessageCreateEvent.class)
                .filter(this::isUserMessage)
                .filter(this::isEnabledForChannel)
                .subscribe(this::handleMessage,
                        err -> Log.errorf(err, "Discord: message listener error"));

        gateway.on(ChatInputInteractionEvent.class)
                .subscribe(this::handleSlashCommand,
                        err -> Log.errorf(err, "Discord: slash command listener error"));

        gateway.on(ButtonInteractionEvent.class, this::handleApprovalButton)
                .subscribe(__ -> {}, err -> Log.errorf(err, "Discord: button listener error"));
    }

    /**
     * Handles a pressed Approve or Deny button. The press becomes the typed command from the user who
     * pressed it and goes through the orchestrator, which applies the allowlist again; a press by anyone
     * else is answered privately and leaves the buttons in place for the owner.
     */
    Mono<Void> handleApprovalButton(ButtonInteractionEvent event) {
        String userId = event.getInteraction().getUser().getId().asString();
        String channelId = event.getInteraction().getChannelId().asString();
        DiscordApprovalButtons.Press press =
                DiscordApprovalButtons.classify(event.getCustomId(), channelId, userId, authService);
        return switch (press.outcome()) {
            case IGNORE -> Mono.empty();
            case REJECT -> {
                Log.warnf("Discord: approval button press rejected userId=%s reason=allowlist", userId);
                yield event.reply("Not authorized.").withEphemeral(true);
            }
            case DECIDE -> event.deferEdit()
                    .then(Mono.defer(() -> Mono.fromCompletionStage(
                            orchestrator.process(press.command()).subscribeAsCompletionStage())))
                    .then(Mono.defer(() -> event.editReply(InteractionReplyEditSpec.create().withComponents())))
                    .onErrorResume(failure -> {
                        Log.errorf(failure, "Discord: approval button press failed userId=%s", userId);
                        return Mono.empty();
                    })
                    .then();
        };
    }

    private boolean isUserMessage(MessageCreateEvent event) {
        return event.getMessage().getAuthor()
                .map(author -> !author.isBot())
                .orElse(false);
    }

    /**
     * Direct messages and guild channels are gated independently: a guild has many voices and going
     * quiet there is a common ask, while the owner's DM is the private channel to the agent and
     * silencing it as a side effect of that would be surprising.
     */
    boolean isEnabledForChannel(MessageCreateEvent event) {
        return isDirectMessage(event) ? config.respondToDirectMessages() : config.respondToAllMessages();
    }

    private boolean isDirectMessage(MessageCreateEvent event) {
        return event.getGuildId().isEmpty();
    }

    private void handleMessage(MessageCreateEvent event) {
        Message msg = event.getMessage();
        MessagingMessage mapped = mapMessage(msg);
        if (mapped.text().isBlank() && mapped.audio().isEmpty() && mapped.attachments().isEmpty()) {
            if (hasAudioAttachment(msg)) {
                send(msg.getChannelId().asString(),
                        "⚠️ I could not process your audio (the download from Discord failed). Could you send it again?")
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
            case "verbose" -> handleVerboseCommand(event);
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

    private void handleVerboseCommand(ChatInputInteractionEvent event) {
        String userId = event.getInteraction().getUser().getId().asString();
        String channelId = event.getInteraction().getChannelId().asString();
        MessagingMessage mapped = new MessagingMessage("discord", channelId, userId, "/verbose", Optional.empty());
        event.deferReply()
                .then(Mono.fromCallable(() -> orchestrator.cycleToolProgress(mapped))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(event::editReply)
                .subscribe(__ -> {}, error -> Log.warnf("Discord verbose command failed: error=%s",
                        error.getClass().getSimpleName()));
    }

    private String helpText() {
        return """
                **Qlawkus** - autonomous engineering agent

                Commands:
                - `/qlawkus ask question:<text>` - ask the agent a question
                - `/qlawkus verbose` - cycle how much tool activity this chat sees
                - `/qlawkus status` - show agent status
                - `/qlawkus help` - show this help

                You can also just send a message in the channel (when `qlawkus.messaging.discord.respond-to-all-messages` is enabled) or in a DM.
                """;
    }

    public MessagingMessage mapMessage(Message msg) {
        String userId = msg.getAuthor().map(User::getId).map(Snowflake::asString).orElse("unknown");
        String channelId = msg.getChannelId().asString();
        String text = msg.getContent();
        Optional<byte[]> audio = extractAudioAttachment(msg);
        var attachments = msg.getAttachments().stream()
                .filter(attachment -> !isAudio(attachment))
                .limit(ChatAttachment.MAX_COUNT + 1)
                .map(attachment -> new ChatAttachment(attachment.getContentType().orElse(null),
                        (long) attachment.getSize(), () -> downloadChatAttachment(attachment.getUrl())))
                .toList();
        return new MessagingMessage("discord", channelId, userId, text, audio, attachments);
    }

    private byte[] downloadChatAttachment(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException failure) {
            throw new AttachmentRejected("Invalid Discord attachment URL.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || !("cdn.discordapp.com".equalsIgnoreCase(uri.getHost())
                    || "media.discordapp.net".equalsIgnoreCase(uri.getHost()))
                || !uri.getPath().startsWith("/attachments/")) {
            throw new AttachmentRejected("Discord attachment URL is outside its media hosts.");
        }
        return mediaDownloader.downloadAttachment(uri);
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
            Log.infof("Discord: downloaded audio attachment (%d bytes, %s)",
                    data.length, att.getContentType().orElse("unknown"));
            return Optional.of(data);
        } catch (Exception e) {
            Log.errorf("Discord: failed to download audio attachment after retries exception=%s",
                    e.getClass().getSimpleName());
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
        GatewayDiscordClient gateway = gatewayClient;
        if (gateway == null) {
            return Uni.createFrom().failure(new IllegalStateException("Discord Gateway not connected"));
        }

        var channel = gateway.getRestClient().getChannelById(Snowflake.of(channelId));
        var request = MessageCreateRequest.builder().content(text)
                .nonce(newNonce()).enforceNonce(true).build();
        return Uni.createFrom().completionStage(() -> Mono.defer(() -> channel.createMessage(request))
                .retryWhen(SEND_RETRY)
                .switchIfEmpty(Mono.error(new IllegalStateException("Discord createMessage returned no message")))
                .toFuture()).replaceWithVoid();
    }

    @Override
    public Uni<Boolean> sendApprovalPrompt(String channelId, String requestId, String text) {
        GatewayDiscordClient gateway = gatewayClient;
        if (gateway == null) {
            return Uni.createFrom().item(false);
        }
        return Uni.createFrom().completionStage(() -> createMessage(gateway, channelId,
                () -> MessageCreateSpec.builder().content(text)
                        .addComponent(DiscordApprovalButtons.row(requestId)).build())
                .switchIfEmpty(Mono.error(new IllegalStateException("Discord did not acknowledge the prompt")))
                .toFuture()).replaceWith(true);
    }

    @Override
    public Optional<ToolProgressSink> progressSink() {
        return Optional.ofNullable(gatewayClient).map(DiscordProgressSink::new);
    }

    @Override
    public Optional<DeliveryFailure> deliveryFailure(Throwable failure) {
        return FailureChain.first(failure, ClientException.class).map(DiscordProviderAdapter::discordFailure);
    }

    private static DeliveryFailure discordFailure(ClientException rejected) {
        Map<String, Object> body = rejected.getErrorResponse().map(ErrorResponse::getFields).orElse(Map.of());
        return new DeliveryFailure(rejected.getStatus().code(), text(body.get("code")), text(body.get("message")));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record DiscordProgressSink(GatewayDiscordClient gateway) implements ToolProgressSink {

        @Override
        public String post(String channelId, String text) {
            Message message = gateway.getChannelById(Snowflake.of(channelId))
                    .ofType(MessageChannel.class)
                    .flatMap(channel -> channel.createMessage(text))
                    .block();
            if (message == null) {
                throw new IllegalStateException("Discord createMessage returned no message");
            }
            return message.getId().asString();
        }

        @Override
        public void edit(String channelId, String messageId, String text) {
            message(channelId, messageId)
                    .flatMap(message -> message.edit(MessageEditSpec.builder().content(text).build()))
                    .block();
        }

        @Override
        public void delete(String channelId, String messageId) {
            message(channelId, messageId).flatMap(message -> message.delete()).block();
        }

        private Mono<Message> message(String channelId, String messageId) {
            return gateway.getMessageById(Snowflake.of(channelId), Snowflake.of(messageId));
        }
    }

    private static String newNonce() {
        return UUID.randomUUID().toString().substring(0, 25);
    }

    /**
     * Creates a message with one enforced nonce across every attempt. The spec is built per attempt
     * because a file body is a stream that a failed attempt may already have consumed.
     */
    private static Mono<Message> createMessage(GatewayDiscordClient gateway, String channelId,
            Supplier<MessageCreateSpec> spec) {
        String nonce = newNonce();
        return Mono.defer(() -> gateway.getChannelById(Snowflake.of(channelId))
                .ofType(MessageChannel.class)
                .flatMap(channel -> channel.createMessage(spec.get().withNonce(nonce).withEnforceNonce(true))))
                .retryWhen(SEND_RETRY);
    }

    @Override
    public Uni<Void> sendFile(String channelId, String filename, String mediaType, byte[] bytes) {
        GatewayDiscordClient gateway = gatewayClient;
        if (gateway == null) {
            return Uni.createFrom().failure(new IllegalStateException("Discord Gateway not connected"));
        }
        return Uni.createFrom().completionStage(() -> createMessage(gateway, channelId,
                () -> voiceMessage(bytes, filename))
                .switchIfEmpty(Mono.error(new IllegalStateException("Discord did not acknowledge the file")))
                .toFuture()).replaceWithVoid();
    }

    @Override
    public Uni<Void> sendVoice(String channelId, byte[] audio, String filename, String fallbackText) {
        GatewayDiscordClient gateway = gatewayClient;
        if (gateway == null) {
            return Uni.createFrom().failure(new IllegalStateException("Discord Gateway not connected"));
        }

        return Uni.createFrom().completionStage(() -> createMessage(gateway, channelId,
                () -> voiceMessage(audio, filename)).toFuture())
                .replaceWithVoid();
    }

    /**
     * Builds the voice-note message. {@code MessageCreateFields.File.of} is used instead of the
     * {@code addFile} shorthand because it keeps the same two-argument shape across discord4j 3.2 and
     * 3.3: 3.3 replaced {@code addFile(String, InputStream)} with an overload that takes an
     * attachment description between the name and the stream.
     */
    static MessageCreateSpec voiceMessage(byte[] audio, String filename) {
        return MessageCreateSpec.builder()
                .addFile(MessageCreateFields.File.of(filename, new ByteArrayInputStream(audio)))
                .build();
    }
}
