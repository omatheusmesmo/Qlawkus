package dev.omatheusmesmo.qlawkus.specialists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecialistCatalogTest {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeValidator() {
        VALIDATORS.close();
    }

    @TempDir
    Path directory;

    @Test
    void persistsOwnerCreatedDefinitionsAndPreservesADisabledProfile() {
        var definition = new SpecialistDefinition("release-review", "Read the notes.", SpecialistsConfig.Tier.FALLBACK, true);
        var catalog = catalog();
        catalog.create(definition);
        assertEquals(List.of(definition), catalog().list());
        assertEquals(409, assertThrows(SpecialistProblem.class, () -> catalog.create(definition)).status());

        var disabled = new SpecialistDefinition(definition.id(), "Read the updated notes.", definition.model(), false);
        catalog.update(disabled);
        assertFalse(catalog().list().getFirst().enabled());
        assertEquals(409, assertThrows(SpecialistProblem.class, () -> catalog().resolve(List.of(definition.id()))).status());

        catalog().delete(definition.id());
        assertEquals(List.of(), catalog().list());
    }

    @Test
    void enforcesTheOwnersModelPolicyAndProtectsAnUnreadableCatalog() throws Exception {
        var invalid = new SpecialistDefinition("remote", "Review the text.", SpecialistsConfig.Tier.PRIMARY, true);
        var catalog = catalog();
        assertEquals(400, assertThrows(SpecialistProblem.class, () -> catalog.create(invalid)).status());
        var valid = new SpecialistDefinition("local", "Review the text.", SpecialistsConfig.Tier.FALLBACK, true);
        Path file = directory.resolve("specialists.json");
        Files.writeString(file, "broken json");

        assertEquals(503, assertThrows(SpecialistProblem.class, () -> catalog.create(valid)).status());
        assertEquals("broken json", Files.readString(file));
    }

    private SpecialistCatalog catalog() {
        return new SpecialistCatalog(directory.resolve("specialists.json"), new ObjectMapper(),
                new SpecialistsConfig() {
                    @Override
                    public List<Tier> allowedModels() {
                        return List.of(Tier.FALLBACK);
                    }

                    @Override
                    public int timeoutSeconds() {
                        return 45;
                    }

                    @Override
                    public int maxOutputTokens() {
                        return 256;
                    }
                }, new SecretRedactor() {
                    @Override
                    public String redact(String text) {
                        return text;
                    }
                }, VALIDATORS.getValidator());
    }
}
