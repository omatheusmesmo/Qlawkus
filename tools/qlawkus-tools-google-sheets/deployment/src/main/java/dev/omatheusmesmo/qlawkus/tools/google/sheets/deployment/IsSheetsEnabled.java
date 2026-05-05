package dev.omatheusmesmo.qlawkus.tools.google.sheets.deployment;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.function.BooleanSupplier;

public class IsSheetsEnabled implements BooleanSupplier {

    @Override
    public boolean getAsBoolean() {
        return ConfigProvider.getConfig()
                .getOptionalValue("qlawkus.google.sheets.enabled", Boolean.class)
                .orElse(false);
    }
}
