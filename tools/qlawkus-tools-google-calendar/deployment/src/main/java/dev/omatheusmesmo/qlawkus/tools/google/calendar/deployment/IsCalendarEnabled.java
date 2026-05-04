package dev.omatheusmesmo.qlawkus.tools.google.calendar.deployment;

import org.eclipse.microprofile.config.ConfigProvider;

import java.util.function.BooleanSupplier;

public class IsCalendarEnabled implements BooleanSupplier {

    @Override
    public boolean getAsBoolean() {
        return ConfigProvider.getConfig()
                .getOptionalValue("qlawkus.google.calendar.enabled", Boolean.class)
                .orElse(false);
    }
}
