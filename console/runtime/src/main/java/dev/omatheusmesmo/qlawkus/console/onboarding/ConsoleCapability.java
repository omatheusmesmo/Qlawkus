package dev.omatheusmesmo.qlawkus.console.onboarding;

import java.util.List;

/**
 * The curated set of capabilities the onboarding wizard can toggle. The running app has no
 * composition catalog (that lives in the build-time Maven plugin, which reads the reactor), so the
 * console ships this list. Adding a new composable capability means adding an entry here.
 *
 * @param name the composition capability name, matching {@code metadata.qlawkus.capability}
 * @param label a short human label for the checkbox
 * @param description one line explaining what enabling it does
 * @param followUp the Phase B step this capability unlocks once it is compiled in
 * @param tokenProperty for {@link FollowUp#MESSAGING_TOKEN}, the keystore alias (config property) of
 *                      the bot token; {@code null} otherwise
 */
public record ConsoleCapability(String name, String label, String description, FollowUp followUp,
                                String tokenProperty) {

    /** The interactive step a capability unlocks only after it has been compiled in (Phase B). */
    public enum FollowUp {
        NONE,
        GOOGLE_OAUTH,
        MESSAGING_TOKEN
    }

    public static final List<ConsoleCapability> CATALOG = List.of(
            new ConsoleCapability("cognition.pgvector", "Postgres memory (pgvector)",
                    "Vector memory on Postgres. Without it the agent uses the database-free markdown stores.",
                    FollowUp.NONE, null),
            new ConsoleCapability("brag", "Brag documents",
                    "Career brag-document tool.", FollowUp.NONE, null),
            new ConsoleCapability("skill-hub", "Skill Hub",
                    "Search, install and publish skills from a remote registry.", FollowUp.NONE, null),
            new ConsoleCapability("google-workspace", "Google Workspace",
                    "Gmail, Calendar, Drive, Sheets and Storage tools.", FollowUp.GOOGLE_OAUTH, null),
            new ConsoleCapability("messaging.discord", "Discord",
                    "Discord chat adapter.", FollowUp.MESSAGING_TOKEN, "qlawkus.messaging.discord.bot-token"),
            new ConsoleCapability("messaging.telegram", "Telegram",
                    "Telegram chat adapter.", FollowUp.MESSAGING_TOKEN, "qlawkus.messaging.telegram.bot-token"));

    public static ConsoleCapability byName(String name) {
        return CATALOG.stream().filter(c -> c.name().equals(name)).findFirst().orElse(null);
    }
}
