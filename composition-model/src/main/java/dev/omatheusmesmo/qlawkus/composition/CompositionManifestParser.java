package dev.omatheusmesmo.qlawkus.composition;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes {@code agent.yml}. The only parser in the codebase for the composition manifest,
 * by design: the pom generator and the running app both call it, so the schema is enforced in one
 * place. Stateless and thread-safe.
 */
public final class CompositionManifestParser {

    private static final ObjectMapper YAML = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    private CompositionManifestParser() {
    }

    /**
     * Parses a manifest from YAML text.
     *
     * @throws InvalidManifestException if the YAML is malformed or violates the schema
     */
    public static CompositionManifest parse(String yaml) {
        try {
            return require(YAML.readValue(yaml, CompositionManifest.class));
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    /**
     * Parses a manifest from an open stream. The caller owns the stream.
     *
     * @throws InvalidManifestException if the YAML is malformed or violates the schema
     */
    public static CompositionManifest parse(InputStream in) {
        try {
            return require(YAML.readValue(in, CompositionManifest.class));
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    /**
     * Reads and parses a manifest from a file.
     *
     * @throws InvalidManifestException if the file is unreadable, malformed, or violates the schema
     */
    public static CompositionManifest parse(Path path) {
        try {
            return parse(Files.readString(path));
        } catch (IOException e) {
            throw new InvalidManifestException("Cannot read manifest at " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Renders a manifest back to YAML. Round-trips with {@link #parse(String)}.
     */
    public static String render(CompositionManifest manifest) {
        try {
            return YAML.writeValueAsString(manifest);
        } catch (IOException e) {
            throw new InvalidManifestException("Cannot render manifest: " + e.getMessage(), e);
        }
    }

    private static CompositionManifest require(CompositionManifest manifest) {
        if (manifest == null) {
            throw new InvalidManifestException("agent.yml is empty");
        }
        return manifest;
    }

    private static InvalidManifestException wrap(IOException e) {
        Throwable cause = e instanceof ValueInstantiationException vie && vie.getCause() != null
                ? vie.getCause()
                : e;
        return new InvalidManifestException("Invalid agent.yml: " + cause.getMessage(), e);
    }
}
