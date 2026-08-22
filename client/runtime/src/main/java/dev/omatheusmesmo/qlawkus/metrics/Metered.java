package dev.omatheusmesmo.qlawkus.metrics;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a bean whose method calls are timed and counted by {@link StoreMeterInterceptor}.
 *
 * <p>Not written by hand on the stores: {@code ClientProcessor} adds it at build time to every class
 * implementing a store SPI, so a new backend is measured the day it is added and cannot be forgotten.
 * Store calls are the one place in the agent where interception works, because stores are injected as
 * their SPI and reached through a CDI proxy, unlike tools.
 */
@Inherited
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface Metered {
}
