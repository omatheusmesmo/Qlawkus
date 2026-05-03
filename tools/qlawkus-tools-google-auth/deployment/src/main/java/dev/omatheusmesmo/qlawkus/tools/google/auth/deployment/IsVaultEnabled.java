package dev.omatheusmesmo.qlawkus.tools.google.auth.deployment;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.function.BooleanSupplier;

public class IsVaultEnabled implements BooleanSupplier {

    @Override
    public boolean getAsBoolean() {
        return ConfigProvider.getConfig()
            .getOptionalValue("qlawkus.google.vault.enabled", Boolean.class)
            .orElse(false);
    }
}
