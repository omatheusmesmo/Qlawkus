package dev.omatheusmesmo.qlawkus.tools.google.auth;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class VaultPassphraseMismatchProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "qlawkus.google.vault.enabled", "true",
            "qlawkus.google.vault.encryption-passphrase", "passphrase-b");
    }
}
