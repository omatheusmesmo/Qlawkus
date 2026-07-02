package dev.omatheusmesmo.qlawkus.composition.plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * A {@link CapabilityCatalog} sourced from the reactor: each module self-describes its capability in
 * its own {@code quarkus-extension.yaml} under {@code metadata.qlawkus.capability}, and this catalog
 * reads that key from every module in the build. Decentralized by construction - the capability name
 * lives with the module, never in a central vocabulary - mirroring how {@code ClientProcessor}
 * discovers {@code @QlawTool} beans across modules.
 *
 * <p>It reads the descriptor <em>source template</em> ({@code src/main/resources/...}), not the
 * generated one, so it works before any module is built and does not depend on the
 * {@code quarkus-extension-maven-plugin} merge. A module with no descriptor, or a descriptor without
 * the capability key, is simply not a capability and is skipped - so core modules (the skeleton) and
 * infra modules (aggregator poms, deployment artifacts) contribute nothing.
 *
 * <p>The same capability name may be declared by several modules (e.g. the six Google modules all
 * announce {@code google-workspace}); the resolver then turns that capability on or off as one unit.
 */
public final class ReactorCatalog implements CapabilityCatalog {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private final List<ReactorModule> modules;

    public ReactorCatalog(List<ReactorModule> modules) {
        this.modules = List.copyOf(modules);
    }

    @Override
    public List<Capability> all() {
        List<Capability> capabilities = new ArrayList<>();
        for (ReactorModule module : modules) {
            String name = readCapability(module);
            if (name != null) {
                capabilities.add(new Capability(name, new Coordinates(module.groupId(), module.artifactId())));
            }
        }
        return capabilities;
    }

    private static String readCapability(ReactorModule module) {
        if (module.descriptor() == null || !Files.isRegularFile(module.descriptor())) {
            return null;
        }
        try {
            JsonNode capability = YAML.readTree(module.descriptor().toFile())
                    .path("metadata").path("qlawkus").path("capability");
            return capability.isTextual() && !capability.asText().isBlank() ? capability.asText() : null;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Cannot read extension descriptor " + module.descriptor() + " for " + module.artifactId(), e);
        }
    }
}
