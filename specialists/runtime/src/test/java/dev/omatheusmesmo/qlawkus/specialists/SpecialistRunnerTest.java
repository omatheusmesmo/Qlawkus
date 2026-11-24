package dev.omatheusmesmo.qlawkus.specialists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.omatheusmesmo.qlawkus.tool.shell.SecretRedactor;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecialistRunnerTest {

    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();

    @TempDir
    Path directory;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Recording standard = new Recording("standard");
    private final Recording primary = new Recording("primary");
    private final Recording secondary = new Recording("secondary");
    private final Recording fallback = new Recording("fallback");

    @AfterAll
    static void closeValidator() {
        VALIDATORS.close();
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void aDefaultTierSpecialistAnswersThroughTheAgentsNormalModelChain() {
        var results = runner(catalog(SpecialistsConfig.Tier.DEFAULT)).run(SpecialistRunner.Pattern.SEQUENCE,
                List.of("reviewer"), "The release notes.");

        assertEquals(SpecialistRunner.Status.COMPLETE, results.getFirst().status());
        assertEquals("standard", results.getFirst().evidence());
        assertEquals(1, standard.requests.size());
        assertEquals(0, fallback.requests.size());
        assertFalse(standard.requests.getFirst().parameters() instanceof OllamaChatRequestParameters);
    }

    @Test
    void aPinnedTierStillReachesOnlyItsOwnModel() {
        var results = runner(catalog(SpecialistsConfig.Tier.FALLBACK)).run(SpecialistRunner.Pattern.SEQUENCE,
                List.of("reviewer"), "The release notes.");

        assertEquals("fallback", results.getFirst().evidence());
        assertEquals(1, fallback.requests.size());
        assertEquals(0, standard.requests.size());
        assertInstanceOf(OllamaChatRequestParameters.class, fallback.requests.getFirst().parameters());
    }

    private SpecialistRunner runner(SpecialistCatalog catalog) {
        return new SpecialistRunner(catalog, executor, standard, primary, secondary, fallback, redactor(), config());
    }

    private SpecialistCatalog catalog(SpecialistsConfig.Tier tier) {
        var catalog = new SpecialistCatalog(directory.resolve("specialists.json"), JsonMapper.builder().build(), config(),
                redactor(), VALIDATORS.getValidator());
        catalog.create(new SpecialistDefinition("reviewer", "Review the text.", tier, true));
        return catalog;
    }

    private static SecretRedactor redactor() {
        return new SecretRedactor() {
            @Override
            public String redact(String text) {
                return text;
            }
        };
    }

    private static SpecialistsConfig config() {
        return new SpecialistsConfig() {
            @Override
            public List<Tier> allowedModels() {
                return List.of(Tier.DEFAULT, Tier.FALLBACK);
            }

            @Override
            public int timeoutSeconds() {
                return 5;
            }

            @Override
            public int maxOutputTokens() {
                return 256;
            }
        };
    }

    private static final class Recording implements ChatModel {

        private final String answer;
        private final List<ChatRequest> requests = new ArrayList<>();

        Recording(String answer) {
            this.answer = answer;
        }

        @Override
        public synchronized ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
        }
    }
}
