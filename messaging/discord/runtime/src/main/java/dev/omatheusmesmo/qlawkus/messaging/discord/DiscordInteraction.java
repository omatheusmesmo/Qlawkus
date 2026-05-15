package dev.omatheusmesmo.qlawkus.messaging.discord;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscordInteraction(
        String id,
        String token,
        int type,
        DiscordUser user,
        DiscordMember member,
        @JsonProperty("channel_id") String channelId,
        DiscordData data
) {
    public static final int TYPE_PING = 1;
    public static final int TYPE_APPLICATION_COMMAND = 2;
    public static final int TYPE_MESSAGE_COMPONENT = 3;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscordUser(
            String id,
            String username
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscordMember(
            DiscordUser user
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscordData(
            String name,
            java.util.List<DiscordOption> options
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiscordOption(
            String name,
            String value
    ) {}

    public String resolvedUserId() {
        if (user != null) return user.id();
        if (member != null && member.user() != null) return member.user().id();
        return "unknown";
    }

    public String resolvedText() {
        if (data == null || data.options() == null || data.options().isEmpty()) return "";
        return data.options().stream()
                .filter(o -> "text".equals(o.name()) || "message".equals(o.name()) || "query".equals(o.name()))
                .map(DiscordOption::value)
                .findFirst()
                .orElse(data.options().get(0).value());
    }
}
