package dev.omatheusmesmo.qlawkus.tools.brag.deployment;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.function.BooleanSupplier;

public class IsBragEnabled implements BooleanSupplier {

    @Override
    public boolean getAsBoolean() {
        return ConfigProvider.getConfig()
                .getOptionalValue("qlawkus.brag.enabled", Boolean.class)
                .orElse(false);
    }
}
