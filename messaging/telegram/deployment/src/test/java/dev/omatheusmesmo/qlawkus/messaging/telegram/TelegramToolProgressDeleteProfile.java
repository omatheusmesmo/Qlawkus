package dev.omatheusmesmo.qlawkus.messaging.telegram;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class TelegramToolProgressDeleteProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "qlawkus.messaging.telegram.bot-token", "test-token",
                "qlawkus.messaging.telegram.mode", "webhook",
                "qlawkus.messaging.tool-progress.mode", "status",
                "qlawkus.messaging.tool-progress.on-complete", "delete");
    }
}
