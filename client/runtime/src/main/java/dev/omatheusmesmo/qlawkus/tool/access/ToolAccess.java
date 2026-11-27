package dev.omatheusmesmo.qlawkus.tool.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the {@link AccessClass} the build infers from the method name of a {@code @Tool}. Use it
 * only where the name says something other than what the tool does. On a method it covers that tool;
 * on a {@code @QlawTool} bean it covers every tool of the bean that has no annotation of its own.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ToolAccess {
    AccessClass value();
}
