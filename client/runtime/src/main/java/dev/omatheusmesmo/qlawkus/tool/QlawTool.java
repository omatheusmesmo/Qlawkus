package dev.omatheusmesmo.qlawkus.tool;

import jakarta.inject.Qualifier;
import jakarta.enterprise.util.Nonbinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface QlawTool {
    /** Whether the global tracing payload switches may capture this tool's inputs and outputs. */
    @Nonbinding
    boolean capturePayload() default true;
}
