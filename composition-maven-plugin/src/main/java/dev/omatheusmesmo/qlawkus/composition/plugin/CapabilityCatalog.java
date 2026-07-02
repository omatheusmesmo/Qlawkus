package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.util.List;

/**
 * The set of capabilities the generator can compose, each mapped to its extension coordinates. It
 * enumerates every known capability without the generator depending on the capability modules
 * themselves, so a deselected capability never has to be on the classpath.
 *
 * <p>The default implementation, {@link ReactorCatalog}, sources this from the Quarkus extension
 * metadata ({@code quarkus-extension.yaml}) of the reactor's Qlawkus extensions. Keeping this an
 * interface leaves room for other resolution strategies (e.g. reading published artifacts for a
 * consumer outside this monorepo) without touching the generator, which consumes only the interface.
 */
public interface CapabilityCatalog {

    /**
     * Every capability the catalog knows about. The resolver applies the manifest posture over this
     * full set, so a capability missing here can never be composed in.
     */
    List<Capability> all();
}
