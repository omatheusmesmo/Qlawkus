package dev.omatheusmesmo.qlawkus.console.voice;

import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.quarkus.websockets.next.HttpUpgradeCheck.CheckResult;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Refuses the voice socket's upgrade unless its {@code Origin} is the console's own origin or one listed
 * in {@code qlawkus.console.voice.allowed-origins}. It runs before the connection exists, so a page on
 * another site cannot open a session even with the owner's cached credentials.
 */
@ApplicationScoped
public class VoiceOriginCheck implements HttpUpgradeCheck {

    private static final int FORBIDDEN = 403;

    private final ConsoleVoiceConfig config;

    @Inject
    public VoiceOriginCheck(ConsoleVoiceConfig config) {
        this.config = config;
    }

    @Override
    public boolean appliesTo(String endpointId) {
        return VoiceSocket.ENDPOINT_ID.equals(endpointId);
    }

    @Override
    public Uni<CheckResult> perform(HttpUpgradeContext context) {
        String origin = context.httpRequest().getHeader("Origin");
        String host = context.httpRequest().getHeader("Host");
        List<String> allowlist = config.allowedOrigins().orElse(List.of());
        return VoiceOriginPolicy.allowed(origin, host, allowlist)
                ? CheckResult.permitUpgrade()
                : CheckResult.rejectUpgrade(FORBIDDEN);
    }
}
