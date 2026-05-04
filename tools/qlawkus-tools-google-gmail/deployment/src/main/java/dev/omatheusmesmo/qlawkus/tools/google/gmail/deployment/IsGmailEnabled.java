package dev.omatheusmesmo.qlawkus.tools.google.gmail.deployment;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.function.BooleanSupplier;

public class IsGmailEnabled implements BooleanSupplier {

    @Override
    public boolean getAsBoolean() {
        return ConfigProvider.getConfig()
                .getOptionalValue("qlawkus.google.gmail.enabled", Boolean.class)
                .orElse(false);
    }
}
