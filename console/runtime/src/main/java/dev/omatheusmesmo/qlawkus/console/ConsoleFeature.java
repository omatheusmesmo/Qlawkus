package dev.omatheusmesmo.qlawkus.console;

/**
 * An optional console feature an extension contributes by registering a bean of this type. The console
 * shows the navigation entry of a feature only when its bean is present, so a distribution without the
 * extension renders no link to a page it does not serve.
 */
public interface ConsoleFeature {

    String name();
}
