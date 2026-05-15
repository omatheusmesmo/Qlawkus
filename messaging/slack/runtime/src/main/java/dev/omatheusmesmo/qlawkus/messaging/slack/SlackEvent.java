package dev.omatheusmesmo.qlawkus.messaging.slack;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlackEvent(
        String type,
        String challenge,
        @JsonProperty("team_id") String teamId,
        @JsonProperty("event") InnerEvent event
) {
    public static final String TYPE_URL_VERIFICATION = "url_verification";
    public static final String TYPE_EVENT_CALLBACK = "event_callback";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InnerEvent(
            String type,
            String user,
            String text,
            String channel,
            @JsonProperty("channel_type") String channelType
    ) {}
}
