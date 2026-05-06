package dev.omatheusmesmo.qlawkus.tools.google.storage.deployment;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.function.BooleanSupplier;

public class IsStorageEnabled implements BooleanSupplier {

    @Override
    public boolean getAsBoolean() {
        return ConfigProvider.getConfig()
                .getOptionalValue("qlawkus.google.storage.enabled", Boolean.class)
                .orElse(false);
    }
}
