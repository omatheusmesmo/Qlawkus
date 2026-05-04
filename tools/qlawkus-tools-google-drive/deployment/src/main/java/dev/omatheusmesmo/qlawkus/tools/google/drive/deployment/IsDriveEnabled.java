package dev.omatheusmesmo.qlawkus.tools.google.drive.deployment;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.function.BooleanSupplier;

public class IsDriveEnabled implements BooleanSupplier {

    @Override
    public boolean getAsBoolean() {
        return ConfigProvider.getConfig()
                .getOptionalValue("qlawkus.google.drive.enabled", Boolean.class)
                .orElse(false);
    }
}
