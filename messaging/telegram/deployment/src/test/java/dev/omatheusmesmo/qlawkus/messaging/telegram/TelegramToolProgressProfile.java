package dev.omatheusmesmo.qlawkus.messaging.telegram;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class TelegramToolProgressProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "qlawkus.messaging.telegram.bot-token", "test-token",
                "qlawkus.messaging.telegram.mode", "webhook");
    }
}
