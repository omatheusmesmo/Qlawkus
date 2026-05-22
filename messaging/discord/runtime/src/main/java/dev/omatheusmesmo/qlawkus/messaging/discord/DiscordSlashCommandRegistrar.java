package dev.omatheusmesmo.qlawkus.messaging.discord;

import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.core.object.command.ApplicationCommandOption;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class DiscordSlashCommandRegistrar {

    public void register(GatewayDiscordClient gateway, String applicationId, Optional<String> guildId) {
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

        if (guildId.isPresent() && !guildId.get().isBlank()) {
            long guildIdNum;
            try {
                guildIdNum = Long.parseLong(guildId.get());
            } catch (NumberFormatException e) {
                Log.errorf("Discord: invalid guild-id '%s', must be numeric", guildId.get());
                return;
            }
            gateway.getRestClient().getApplicationService()
                    .createGuildApplicationCommand(appId, guildIdNum, qlawkusCommand)
                    .subscribe(
                            cmd -> Log.infof("Discord: slash command /qlawkus registered to guild=%s (id=%s, instant)",
                                    guildId.get(), cmd.id()),
                            err -> Log.errorf(err, "Discord: failed to register guild slash command"));
        } else {
            gateway.getRestClient().getApplicationService()
                    .createGlobalApplicationCommand(appId, qlawkusCommand)
                    .subscribe(
                            cmd -> Log.infof("Discord: slash command /qlawkus registered globally (id=%s, may take up to 1h to propagate)", cmd.id()),
                            err -> Log.errorf(err, "Discord: failed to register global slash command"));
        }
    }
}
