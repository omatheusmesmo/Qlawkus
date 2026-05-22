package dev.omatheusmesmo.qlawkus.tools.google.auth;

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
    private volatile MemoryCredential memoryStore;
    private volatile boolean renewalScheduled;

    /**
     * Returns true when persistent encrypted vault should be used.
     * Returns false when running in ephemeral in-memory mode (vault disabled or no passphrase).
     */
    private boolean useVault() {
        return vaultConfig.enabled() && vaultConfig.encryptionPassphrase().isPresent()
                && !vaultConfig.encryptionPassphrase().get().isBlank();
    }

    void onRefreshTokenCaptured(@ObservesAsync RefreshTokenCapturedEvent event) {
        if (useVault()) {
            persistRefreshToken(event.refreshToken());
        } else {
            memoryStore = new MemoryCredential(event.refreshToken(), null, null);
            Log.warn("⚠️ Refresh token captured in MEMORY ONLY (vault disabled or no encryption-passphrase). "
                    + "Token will be LOST on container restart. To persist, set qlawkus.google.vault.enabled=true "
                    + "and qlawkus.google.vault.encryption-passphrase.");
        }
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

    public void renewAccessToken() {
        if (useVault()) {
            renewVaultAccessToken();
        } else {
            renewMemoryAccessToken();
        }
    }

    @Transactional
    void renewVaultAccessToken() {
        GoogleCredential credential = GoogleCredential.findByProvider("google");
        if (credential == null) {
            Log.debug("No Google credential stored, skipping renewal");
            return;
        }

        AesEncryptor enc = getEncryptor();
        String refreshToken = enc.decrypt(credential.encryptedRefreshToken);

        try {
            TokenResponse response = deviceFlowClient.refreshAccessToken(
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

    void renewMemoryAccessToken() {
        MemoryCredential current = memoryStore;
        if (current == null || current.refreshToken() == null) {
            Log.debug("No in-memory Google credential, skipping renewal");
            return;
        }

        try {
            TokenResponse response = deviceFlowClient.refreshAccessToken(
                    authConfig.clientId(),
                    authConfig.clientSecret(),
                    current.refreshToken(),
                    "refresh_token");

            if (response.error() != null) {
                Log.errorf("In-memory token renewal error: %s - %s", response.error(), response.errorDescription());
                return;
            }

            String newRefresh = response.refreshToken() != null ? response.refreshToken() : current.refreshToken();
            memoryStore = new MemoryCredential(
                    newRefresh,
                    response.accessToken(),
                    Instant.now().plusSeconds(response.expiresIn()));

            Log.infof("In-memory Google access token renewed, expires in %ds", response.expiresIn());
        } catch (Exception e) {
            Log.errorf(e, "Failed to renew in-memory Google access token");
        }
    }

    public String getAccessToken() {
        if (useVault()) {
            return getVaultAccessToken();
        }
        return getMemoryAccessToken();
    }

    private String getVaultAccessToken() {
        GoogleCredential credential = GoogleCredential.findByProvider("google");
        if (credential == null) return null;

        if (credential.expiresAt != null && credential.expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return credential.accessToken;
        }

        renewVaultAccessToken();

        credential = GoogleCredential.findByProvider("google");
        return credential != null ? credential.accessToken : null;
    }

    private String getMemoryAccessToken() {
        MemoryCredential current = memoryStore;
        if (current == null) return null;

        if (current.expiresAt() != null && current.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return current.accessToken();
        }

        renewMemoryAccessToken();
        MemoryCredential refreshed = memoryStore;
        return refreshed != null ? refreshed.accessToken() : null;
    }

    public String getDecryptedRefreshToken() {
        if (useVault()) {
            GoogleCredential credential = GoogleCredential.findByProvider("google");
            return credential != null ? getEncryptor().decrypt(credential.encryptedRefreshToken) : null;
        }
        MemoryCredential current = memoryStore;
        return current != null ? current.refreshToken() : null;
    }

    private AesEncryptor getEncryptor() {
        if (encryptor == null) {
            String passphrase = vaultConfig.encryptionPassphrase()
                    .orElseThrow(() -> new IllegalStateException(
                            "qlawkus.google.vault.encryption-passphrase is required when vault is enabled"));
            encryptor = new AesEncryptor(passphrase);
        }
        return encryptor;
    }

    private synchronized void scheduleRenewal() {
        if (renewalScheduled) return;
        String interval = vaultConfig.renewalIntervalSeconds() + "s";
        scheduler.newJob("google-token-renewal")
                .setInterval(interval)
                .setTask(ctx -> renewAccessToken())
                .schedule();
        renewalScheduled = true;
        Log.infof("Scheduled Google token renewal every %s (mode: %s)",
                interval, useVault() ? "vault" : "memory");
    }

    private record MemoryCredential(String refreshToken, String accessToken, Instant expiresAt) {}
}
