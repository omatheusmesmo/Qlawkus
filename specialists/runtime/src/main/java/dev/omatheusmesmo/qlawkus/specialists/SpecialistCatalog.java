package dev.omatheusmesmo.qlawkus.specialists;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.config.AgentConfig;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
class SpecialistCatalog {

    private static final int MAX_ENTRIES = 24;
    private static final int MAX_FILE_BYTES = 192 * 1_024;
    private final Path catalog;
    private final ObjectMapper json;
    private final SpecialistsConfig config;
    private final SecretRedactor redactor;
    private final Validator validator;

    @Inject
    SpecialistCatalog(AgentConfig agent, ObjectMapper json, SpecialistsConfig config, SecretRedactor redactor,
            Validator validator) {
        this(Path.of(agent.state().root(), "specialists.json"), json, config, redactor, validator);
    }

    SpecialistCatalog(Path catalog, ObjectMapper json, SpecialistsConfig config, SecretRedactor redactor,
            Validator validator) {
        this.catalog = catalog;
        this.json = json;
        this.config = config;
        this.redactor = redactor;
        this.validator = validator;
    }

    synchronized List<SpecialistDefinition> list() {
        return List.copyOf(load().values());
    }

    List<SpecialistsConfig.Tier> models() {
        return config.allowedModels().stream().distinct().toList();
    }

    synchronized SpecialistDefinition create(SpecialistDefinition definition) {
        validate(definition);
        var definitions = load();
        if (definitions.containsKey(definition.id())) {
            throw new SpecialistProblem(409, "That specialist id already exists. List the catalog to reuse it.");
        }
        if (definitions.size() >= MAX_ENTRIES) {
            throw new SpecialistProblem(409, "The catalog holds at most 24 specialists.");
        }
        return write(definitions, definition);
    }

    synchronized SpecialistDefinition update(SpecialistDefinition definition) {
        validate(definition);
        var definitions = load();
        if (!definitions.containsKey(definition.id())) {
            throw new SpecialistProblem(404, "That specialist does not exist.");
        }
        return write(definitions, definition);
    }

    synchronized void delete(String id) {
        var definitions = load();
        if (definitions.remove(id) == null) {
            throw new SpecialistProblem(404, "That specialist does not exist.");
        }
        persist(definitions);
    }

    synchronized List<SpecialistDefinition> resolve(List<String> ids) {
        var definitions = load();
        return ids.stream().map(id -> {
            var definition = definitions.get(id);
            if (definition == null || !definition.enabled()) {
                throw new SpecialistProblem(409, "Choose specialists that exist and are enabled.");
            }
            requireAllowed(definition.model());
            return definition;
        }).toList();
    }

    private SpecialistDefinition write(Map<String, SpecialistDefinition> definitions, SpecialistDefinition definition) {
        if (definition.enabled()) {
            requireAllowed(definition.model());
        }
        if (!definition.instructions().equals(redactor.redact(definition.instructions()))
                || definition.instructions().contains("PRIVATE KEY-----")) {
            throw SpecialistProblem.invalid("Specialist instructions must not contain credentials.");
        }
        definitions.put(definition.id(), definition);
        persist(definitions);
        return definition;
    }

    private void requireAllowed(SpecialistsConfig.Tier tier) {
        if (!config.allowedModels().contains(tier)) {
            throw SpecialistProblem.invalid("Choose a model tier permitted by the owner.");
        }
    }

    private void validate(SpecialistDefinition definition) {
        var violations = validator.validate(definition);
        if (!violations.isEmpty()) {
            var violation = violations.iterator().next();
            throw SpecialistProblem.invalid(violation.getPropertyPath() + " " + violation.getMessage() + ".");
        }
    }

    private Map<String, SpecialistDefinition> load() {
        var definitions = new TreeMap<String, SpecialistDefinition>();
        if (Files.notExists(catalog, LinkOption.NOFOLLOW_LINKS)) {
            return definitions;
        }
        try {
            if (!Files.isRegularFile(catalog, LinkOption.NOFOLLOW_LINKS) || Files.size(catalog) > MAX_FILE_BYTES) {
                throw new IOException("Invalid catalog file");
            }
            try (var input = Files.newInputStream(catalog, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
                if (bytes.length > MAX_FILE_BYTES) {
                    throw new IOException("Catalog exceeds byte budget");
                }
                SpecialistDefinition[] stored = json.readValue(bytes, SpecialistDefinition[].class);
                if (stored == null || stored.length > MAX_ENTRIES) {
                    throw new IOException("Catalog exceeds size budget");
                }
                for (SpecialistDefinition definition : stored) {
                    if (definition == null || !validator.validate(definition).isEmpty()
                            || definitions.putIfAbsent(definition.id(), definition) != null) {
                        throw new IOException("Invalid catalog entry");
                    }
                }
            }
            return definitions;
        } catch (IOException | RuntimeException failure) {
            Log.warnf("Specialist catalog read failed error=%s", failure.getClass().getSimpleName());
            throw SpecialistProblem.unavailable();
        }
    }

    private void persist(Map<String, SpecialistDefinition> definitions) {
        try {
            Files.createDirectories(catalog.getParent());
            Path temporary = Files.createTempFile(catalog.getParent(), "specialists-", ".tmp");
            try {
                json.writeValue(temporary.toFile(), definitions.values());
                Files.move(temporary, catalog, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            Log.warnf("Specialist catalog write failed error=%s", failure.getClass().getSimpleName());
            throw SpecialistProblem.unavailable();
        }
    }
}
