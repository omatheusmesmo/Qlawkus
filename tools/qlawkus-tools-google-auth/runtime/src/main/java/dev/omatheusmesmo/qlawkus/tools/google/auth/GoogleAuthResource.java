package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Path("/api/google/auth")
public class GoogleAuthResource {

    @Inject
    @RestClient
    GoogleDeviceFlowClient deviceFlowClient;

    @Inject
    GoogleAuthConfig config;

    @Inject
    Event<RefreshTokenCapturedEvent> refreshTokenEvent;

    private final ExecutorService pollExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "google-device-flow-poll");
        t.setDaemon(true);
        return t;
    });

    @GET
    public DeviceCodeResponse authorize() {
        Log.info("Initiating Google Device Authorization Grant flow");

        DeviceCodeResponse codeResponse = deviceFlowClient.requestDeviceCode(
                config.clientId(),
                config.clientSecret(),
                config.scopes());

        Log.infof("Device code issued. Visit %s and enter code %s",
                codeResponse.verificationUrl(), codeResponse.userCode());

        pollExecutor.submit(() -> pollForToken(codeResponse.deviceCode(), codeResponse.interval()));

        return codeResponse;
    }

    private void pollForToken(String deviceCode, int intervalSeconds) {
        try {
            Thread.sleep(Duration.ofSeconds(intervalSeconds));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        TokenResponse response = deviceFlowClient.retrieveToken(
                config.clientId(),
                config.clientSecret(),
                deviceCode,
                "urn:ietf:params:oauth:grant-type:device_code");

        if (response.error() != null) {
            switch (response.error()) {
                case "authorization_pending" -> pollForToken(deviceCode, intervalSeconds);
                case "slow_down" -> pollForToken(deviceCode, intervalSeconds + 5);
                default -> Log.errorf("Device flow error: %s - %s",
                        response.error(), response.errorDescription());
            }
            return;
        }

        if (response.refreshToken() != null) {
            Log.info("Refresh token captured from Device Flow, firing RefreshTokenCapturedEvent");
            refreshTokenEvent.fireAsync(new RefreshTokenCapturedEvent(response.refreshToken()));
        } else {
            Log.warn("Device Flow completed but no refresh token returned");
        }
    }
}
