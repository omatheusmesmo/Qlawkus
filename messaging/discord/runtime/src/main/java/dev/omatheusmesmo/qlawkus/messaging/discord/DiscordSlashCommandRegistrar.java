package dev.omatheusmesmo.qlawkus.messaging.discord;

import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.core.object.command.ApplicationCommandOption;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DiscordSlashCommandRegistrar {

    public void register(GatewayDiscordClient gateway, String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            Log.warn("Discord: application-id not configured, slash commands will not be registered");
            return;
        }

        ApplicationCommandRequest qlawkusCommand = ApplicationCommandRequest.builder()
                .name("qlawkus")
                .description("Interact with the Qlawkus autonomous engineering agent")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("ask")
                        .description("Ask the agent a question")
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .addOption(ApplicationCommandOptionData.builder()
                                .name("question")
                                .description("Your question")
                                .type(ApplicationCommandOption.Type.STRING.getValue())
                                .required(true)
                                .build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("status")
                        .description("Show agent health")
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("help")
                        .description("Show usage info")
                        .type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
                        .build())
                .build();

        long appId;
        try {
            appId = Long.parseLong(applicationId);
        } catch (NumberFormatException e) {
            Log.errorf("Discord: invalid application-id '%s', must be numeric", applicationId);
            return;
        }

        gateway.getRestClient().getApplicationService()
                .createGlobalApplicationCommand(appId, qlawkusCommand)
                .subscribe(
                        cmd -> Log.infof("Discord: slash command /qlawkus registered (id=%s)", cmd.id()),
                        err -> Log.errorf(err, "Discord: failed to register /qlawkus slash command"));
    }
}
