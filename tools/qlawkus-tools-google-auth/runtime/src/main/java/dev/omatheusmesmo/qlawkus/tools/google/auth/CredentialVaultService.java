package dev.omatheusmesmo.qlawkus.tools.google.auth;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;

@ApplicationScoped
public class CredentialVaultService {

    @Inject
    GoogleVaultConfig vaultConfig;

    @Inject
    GoogleAuthConfig authConfig;

    @Inject
    @RestClient
    GoogleDeviceFlowClient deviceFlowClient;

    @Inject
    Scheduler scheduler;

    private volatile AesEncryptor encryptor;

    void onRefreshTokenCaptured(@ObservesAsync RefreshTokenCapturedEvent event) {
        if (!vaultConfig.enabled()) {
            Log.info("Google vault disabled, skipping refresh token persistence");
            return;
        }
        persistRefreshToken(event.refreshToken());
        scheduleRenewal();
    }

    @Transactional
    public void persistRefreshToken(String refreshToken) {
        AesEncryptor enc = getEncryptor();
        String encrypted = enc.encrypt(refreshToken);

        GoogleCredential credential = GoogleCredential.findByProvider("google");
        if (credential == null) {
            credential = new GoogleCredential();
            credential.provider = "google";
        }
        credential.encryptedRefreshToken = encrypted;
        credential.persist();

        Log.info("Google refresh token persisted (encrypted)");
    }

    @Transactional
    public void renewAccessToken() {
        if (!vaultConfig.enabled()) return;

        GoogleCredential credential = GoogleCredential.findByProvider("google");
        if (credential == null) {
            Log.debug("No Google credential stored, skipping renewal");
            return;
        }

        AesEncryptor enc = getEncryptor();
        String refreshToken = enc.decrypt(credential.encryptedRefreshToken);

        try {
            TokenResponse response = deviceFlowClient.retrieveToken(
                    authConfig.clientId(),
                    authConfig.clientSecret(),
                    refreshToken,
                    "refresh_token");

            if (response.error() != null) {
                Log.errorf("Token renewal error: %s - %s", response.error(), response.errorDescription());
                return;
            }

            credential.accessToken = response.accessToken();
            credential.tokenType = response.tokenType();
            credential.expiresAt = Instant.now().plusSeconds(response.expiresIn());

            if (response.refreshToken() != null && !response.refreshToken().equals(refreshToken)) {
                credential.encryptedRefreshToken = enc.encrypt(response.refreshToken());
                Log.info("Google refresh token rotated");
            }

            Log.infof("Google access token renewed, expires in %ds", response.expiresIn());
        } catch (Exception e) {
            Log.errorf(e, "Failed to renew Google access token");
        }
    }

    public String getAccessToken() {
        if (!vaultConfig.enabled()) return null;

        GoogleCredential credential = GoogleCredential.findByProvider("google");
        if (credential == null) return null;

        if (credential.expiresAt != null && credential.expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return credential.accessToken;
        }

        renewAccessToken();

        credential = GoogleCredential.findByProvider("google");
        return credential != null ? credential.accessToken : null;
    }

    public String getDecryptedRefreshToken() {
        if (!vaultConfig.enabled()) return null;

        GoogleCredential credential = GoogleCredential.findByProvider("google");
        if (credential == null) return null;

        return getEncryptor().decrypt(credential.encryptedRefreshToken);
    }

    private AesEncryptor getEncryptor() {
        if (encryptor == null) {
            String passphrase = vaultConfig.encryptionPassphrase()
                    .orElseThrow(() -> new IllegalStateException("qlawkus.google.vault.encryption-passphrase is required when vault is enabled"));
            encryptor = new AesEncryptor(passphrase);
        }
        return encryptor;
    }

    private void scheduleRenewal() {
        String interval = vaultConfig.renewalIntervalSeconds() + "s";
        scheduler.newJob("google-token-renewal")
                .setInterval(interval)
                .setTask(ctx -> renewAccessToken())
                .schedule();
        Log.infof("Scheduled Google token renewal every %s", interval);
    }
}
