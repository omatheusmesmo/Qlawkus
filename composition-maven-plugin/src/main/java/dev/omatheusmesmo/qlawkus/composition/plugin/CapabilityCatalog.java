package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.util.List;

/**
 * The set of capabilities the generator can compose, each mapped to its extension coordinates. This
 * is the registry side of the design (issue #74): it enumerates every known capability without the
 * generator depending on the capability modules themselves, so a deselected capability never has to
 * be on the classpath.
 *
 * <p>The #74 implementation sources this from the Quarkus extension metadata
 * ({@code quarkus-extension.yaml}) of the Qlawkus extensions. The generator (#69) consumes the
 * interface, so the two evolve independently.
 */
public interface CapabilityCatalog {

    /**
     * Every capability the catalog knows about. The resolver applies the manifest posture over this
     * full set, so a capability missing here can never be composed in.
     */
    List<Capability> all();
}
